package com.example.moasis.ai.melange

import android.content.Context
import android.util.Log
import com.example.moasis.ai.model.VisionDetectionResult
import com.example.moasis.ai.onnx.OnnxYoloDetectionEngine
import com.example.moasis.ai.orchestrator.VisionDetectionEngine
import com.example.moasis.domain.model.VisionTaskType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-demand YOLOE detector for kit / object scans.
 *
 * Uses ONNX Runtime (CPU) instead of the Zetic / Melange NPU stack,
 * so loading this model does not compete with the always-resident
 * Qwen LLM + Embedding models on the Zetic NPU.
 *
 * The ONNX model is loaded from app assets at the start of each
 * [detect] call and released immediately afterwards. The cost is
 * per-call load latency (~1-2 s); the gain is zero idle memory.
 *
 * Concurrency: [detect] calls are serialized through an internal mutex
 * so we never load YOLOE twice in parallel.
 */
class ScopedKitDetectionEngine internal constructor(
    private val modelAssetName: String,
    private val sessionFactory: () -> KitInferenceSession,
) : VisionDetectionEngine {

    /** Production constructor: each detect() builds a fresh ONNX session. */
    constructor(
        appContext: Context,
        modelAssetName: String,
    ) : this(
        modelAssetName = modelAssetName,
        sessionFactory = { OnnxKitInferenceSession(appContext, modelAssetName) },
    ) {
        assetVerified = runCatching {
            appContext.assets.open(modelAssetName).close()
        }.isSuccess
    }

    /**
     * `true` only when the named ONNX asset actually exists in the APK.
     * The internal-constructor path (used by tests with fake sessions)
     * skips this check and defaults to `true`.
     */
    private var assetVerified: Boolean = true

    private val mutex = Mutex()

    override fun isConfigured(): Boolean = modelAssetName.isNotBlank() && assetVerified

    // The session is never retained between calls.
    override fun isPreparedInMemory(): Boolean = false

    /**
     * Config-only check. We deliberately do not pre-load the model here:
     * the whole point of this engine is to keep the model out of memory
     * until detection actually runs.
     */
    override suspend fun prepareIfNeeded(): Result<Unit> {
        return if (isConfigured()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Vision ONNX model asset is not configured or not found in assets."))
        }
    }

    override suspend fun detect(
        imagePath: String,
        taskType: VisionTaskType,
    ): Result<VisionDetectionResult> {
        if (!isConfigured()) {
            return Result.failure(IllegalStateException("Vision ONNX model asset is not configured or not found in assets."))
        }
        return mutex.withLock {
            val session = sessionFactory()
            try {
                val warmup = session.warmup()
                val warmupError = warmup.exceptionOrNull()
                if (warmupError != null) {
                    safeWarn("Kit detector load failed: ${warmupError.message ?: warmupError::class.java.simpleName}")
                    Result.failure(warmupError)
                } else {
                    session.detect(imagePath = imagePath, taskType = taskType)
                }
            } catch (t: Throwable) {
                safeWarn("Kit detector inference failed: ${t.message ?: t::class.java.simpleName}")
                Result.failure(t)
            } finally {
                // Always release the ONNX session so RAM stays free for the
                // resident Zetic LLM/embedding stack.
                runCatching { session.close() }
                safeDebug("Kit detector ONNX session released.")
            }
        }
    }

    // Wrap android.util.Log so unit tests on the JVM do not blow up.
    private fun safeWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun safeDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    companion object {
        private const val TAG = "ScopedKitDetector"
    }
}

/**
 * Single-use vision inference session. Owns one ONNX model instance
 * and releases it on [close]. Used by [ScopedKitDetectionEngine] to scope
 * the model lifetime to one detect call.
 */
interface KitInferenceSession : AutoCloseable {
    suspend fun warmup(): Result<Unit>
    suspend fun detect(
        imagePath: String,
        taskType: VisionTaskType,
    ): Result<VisionDetectionResult>
}

/**
 * Production [KitInferenceSession] backed by [OnnxYoloDetectionEngine].
 * Loads the ONNX model from assets on [warmup] and releases on [close].
 */
internal class OnnxKitInferenceSession(
    appContext: Context,
    modelAssetName: String,
) : KitInferenceSession {
    private val engine = OnnxYoloDetectionEngine(
        appContext = appContext,
        modelAssetName = modelAssetName,
    )

    override suspend fun warmup(): Result<Unit> = engine.prepareIfNeeded()

    override suspend fun detect(
        imagePath: String,
        taskType: VisionTaskType,
    ): Result<VisionDetectionResult> = engine.detect(imagePath = imagePath, taskType = taskType)

    override fun close() {
        runCatching { engine.close() }
    }
}
