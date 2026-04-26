package com.example.moasis.ai.melange

import android.content.Context
import android.util.Log
import com.zeticai.mlange.core.model.ModelMode
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.model.ZeticMLangeModelMetadata

class MelangeVisionModelManager(
    context: Context,
    private val config: MelangeVisionRuntimeConfig,
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var cachedSession: VisionModelSession? = null

    fun isConfigured(): Boolean = config.isConfigured

    fun isPreparedInMemory(): Boolean = cachedSession != null

    fun peekSession(): VisionModelSession? = cachedSession

    fun configuredModelLabel(): String {
        val versionSuffix = config.modelVersion?.let { " v$it" }.orEmpty()
        return "${config.modelName}$versionSuffix (${config.modelModeName})"
    }

    fun getOrCreateSession(): Result<VisionModelSession> {
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("Vision runtime is not configured."))
        }

        cachedSession?.let { return Result.success(it) }

        synchronized(lock) {
            cachedSession?.let { return Result.success(it) }

            Log.d(
                TAG,
                "Creating detector session model=${config.modelName} version=${config.modelVersion ?: "latest"} mode=${config.modelModeName}",
            )
            return runCatching {
                val model = MelangeInitCoordinator.runExclusive(
                    modelType = "vision",
                    modelName = config.modelName,
                ) {
                    ZeticMLangeModel(
                        context = appContext,
                        personalKey = config.personalKey,
                        name = config.modelName,
                        version = config.modelVersion,
                        modelMode = resolveModelMode(config.modelModeName),
                    )
                }
                val session = VisionModelSession(
                    model = model,
                    metadata = model.readMetadata(),
                )
                cachedSession = session
                Log.d(TAG, "Detector session ready for ${config.modelName}")
                session
            }
        }
    }

    fun release() {
        synchronized(lock) {
            runCatching { cachedSession?.model?.close() }
            cachedSession = null
        }
    }

    private fun resolveModelMode(modeName: String): ModelMode {
        return runCatching { ModelMode.valueOf(modeName) }
            .getOrDefault(ModelMode.RUN_AUTO)
    }

    private fun ZeticMLangeModel.readMetadata(): ZeticMLangeModelMetadata {
        val field = ZeticMLangeModel::class.java.getDeclaredField("metadata")
        field.isAccessible = true
        return field.get(this) as ZeticMLangeModelMetadata
    }

    companion object {
        private const val TAG = "MelangeVisionModel"
    }
}

data class VisionModelSession(
    val model: ZeticMLangeModel,
    val metadata: ZeticMLangeModelMetadata,
)
