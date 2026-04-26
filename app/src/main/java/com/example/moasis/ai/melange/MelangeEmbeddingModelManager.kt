package com.example.moasis.ai.melange

import android.content.Context
import android.util.Log
import com.zeticai.mlange.core.model.ModelMode
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.model.ZeticMLangeModelMetadata

class MelangeEmbeddingModelManager(
    context: Context,
    private val config: MelangeEmbeddingRuntimeConfig,
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var cachedSession: EmbeddingModelSession? = null

    fun isConfigured(): Boolean = config.isConfigured

    fun isPreparedInMemory(): Boolean = cachedSession != null

    fun peekSession(): EmbeddingModelSession? = cachedSession

    fun configuredModelLabel(): String {
        val versionSuffix = config.modelVersion?.let { " v$it" }.orEmpty()
        return "${config.modelName}$versionSuffix (${config.modelModeName})"
    }

    fun getOrCreateSession(): Result<EmbeddingModelSession> {
        if (!config.isConfigured) {
            Log.w(TAG, "Embedding runtime is not configured")
            return Result.failure(IllegalStateException("Embedding runtime is not configured."))
        }

        cachedSession?.let {
            Log.d(TAG, "Reusing cached embedding session for ${config.modelName}")
            return Result.success(it)
        }

        synchronized(lock) {
            cachedSession?.let {
                Log.d(TAG, "Reusing cached embedding session for ${config.modelName} after lock")
                return Result.success(it)
            }

            Log.d(
                TAG,
                "Creating embedding session model=${config.modelName} version=${config.modelVersion ?: "latest"} mode=${config.modelModeName}",
            )
            return runCatching {
                val model = MelangeInitCoordinator.runExclusive(
                    modelType = "embedding",
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
                val metadata = model.readMetadata()
                Log.d(
                    TAG,
                    "Embedding model metadata loaded inputs=${metadata.profileResult.inputTensors.map { it.name to it.shape }} outputs=${metadata.profileResult.outputTensors.map { it.name to it.shape }}",
                )
                EmbeddingModelSession(model = model, metadata = metadata)
            }.onSuccess {
                cachedSession = it
                Log.d(TAG, "Embedding session ready for ${config.modelName}")
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
        return runCatching { ModelMode.valueOf(modeName) }.getOrDefault(ModelMode.RUN_AUTO)
    }

    private fun ZeticMLangeModel.readMetadata(): ZeticMLangeModelMetadata {
        val field = ZeticMLangeModel::class.java.getDeclaredField("metadata")
        field.isAccessible = true
        return field.get(this) as ZeticMLangeModelMetadata
    }

    companion object {
        private const val TAG = "MelangeEmbeddingModel"
    }
}

data class EmbeddingModelSession(
    val model: ZeticMLangeModel,
    val metadata: ZeticMLangeModelMetadata,
)
