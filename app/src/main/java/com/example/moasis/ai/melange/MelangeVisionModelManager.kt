package com.example.moasis.ai.melange

import android.content.Context
import android.util.Log
import com.zeticai.mlange.core.model.APType
import com.zeticai.mlange.core.model.ModelMode
import com.zeticai.mlange.core.model.QuantType
import com.zeticai.mlange.core.model.Target
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
        val targetSuffix = config.targetName?.takeIf { it.isNotBlank() } ?: config.modelModeName
        val apSuffix = config.apTypeName?.takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty()
        val quantSuffix = config.quantTypeName?.takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty()
        return "${config.modelName}$versionSuffix ($targetSuffix$apSuffix$quantSuffix)"
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
                "Creating detector session model=${config.modelName} version=${config.modelVersion ?: "latest"} mode=${config.modelModeName} target=${config.targetName ?: "auto"} ap=${config.apTypeName ?: "auto"} quant=${config.quantTypeName ?: "auto"}",
            )
            return runCatching {
                val model = MelangeInitCoordinator.runExclusive(
                    modelType = "vision",
                    modelName = config.modelName,
                ) {
                    createModel()
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

    private fun createModel(): ZeticMLangeModel {
        val targetName = config.targetName?.takeIf { it.isNotBlank() }
        val apTypeName = config.apTypeName?.takeIf { it.isNotBlank() }
        val quantTypeName = config.quantTypeName?.takeIf { it.isNotBlank() }

        if (targetName != null && apTypeName != null) {
            return ZeticMLangeModel(
                context = appContext,
                personalKey = config.personalKey,
                name = config.modelName,
                version = config.modelVersion,
                target = resolveTarget(targetName),
                apType = resolveApType(apTypeName),
            )
        }

        return ZeticMLangeModel(
            context = appContext,
            personalKey = config.personalKey,
            name = config.modelName,
            version = config.modelVersion,
            modelMode = resolveModelMode(config.modelModeName),
            quantType = quantTypeName?.let(::resolveQuantType),
        )
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

    private fun resolveQuantType(quantTypeName: String): QuantType {
        return runCatching { QuantType.valueOf(quantTypeName) }
            .getOrDefault(QuantType.FP32)
    }

    private fun resolveTarget(targetName: String): Target {
        return runCatching { Target.valueOf(targetName) }
            .getOrElse { error("Unsupported vision target: $targetName") }
    }

    private fun resolveApType(apTypeName: String): APType {
        return runCatching { APType.valueOf(apTypeName) }
            .getOrElse { error("Unsupported vision APType: $apTypeName") }
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
