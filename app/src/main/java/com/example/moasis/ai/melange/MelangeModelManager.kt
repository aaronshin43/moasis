package com.example.moasis.ai.melange

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.zeticai.mlange.core.model.ModelLoadingStatus
import com.zeticai.mlange.core.model.llm.LLMModelMode
import com.zeticai.mlange.core.model.llm.ZeticMLangeLLMModel

class MelangeModelManager(
    context: Context,
    private val config: MelangeRuntimeConfig,
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var cachedModel: ZeticMLangeLLMModel? = null

    @Volatile
    private var lastProgress: Float = 0f

    fun isConfigured(): Boolean = config.isConfigured

    fun isPreparedInMemory(): Boolean = cachedModel != null

    fun hasInternetConnection(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getOrCreateModel(
        onProgress: ((Float) -> Unit)? = null,
        onStatusChanged: ((ModelLoadingStatus) -> Unit)? = null,
    ): Result<ZeticMLangeLLMModel> {
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("Melange runtime is not configured."))
        }

        cachedModel?.let {
            onProgress?.invoke(1f)
            onStatusChanged?.invoke(ModelLoadingStatus.COMPLETED)
            return Result.success(it)
        }

        synchronized(lock) {
            cachedModel?.let {
                onProgress?.invoke(1f)
                onStatusChanged?.invoke(ModelLoadingStatus.COMPLETED)
                return Result.success(it)
            }

            val model = runCatching {
                onStatusChanged?.invoke(ModelLoadingStatus.PENDING)
                ZeticMLangeLLMModel(
                    context = appContext,
                    personalKey = config.personalKey,
                    name = config.modelName,
                    version = config.modelVersion,
                    modelMode = resolveModelMode(config.modelModeName),
                    onProgress = { progress ->
                        lastProgress = progress
                        onProgress?.invoke(progress)
                        // Log.d(TAG, "Melange init progress=${"%.2f".format(progress)}")
                    },
                    onStatusChanged = { status ->
                        onStatusChanged?.invoke(status)
                        Log.d(TAG, "Melange status=$status")
                    },
                )
            }.getOrElse { return Result.failure(it) }

            cachedModel = model
            lastProgress = 1f
            onProgress?.invoke(1f)
            onStatusChanged?.invoke(ModelLoadingStatus.COMPLETED)
            return Result.success(model)
        }
    }

    fun release() {
        synchronized(lock) {
            runCatching { cachedModel?.deinit() }
            cachedModel = null
            lastProgress = 0f
        }
    }

    private fun resolveModelMode(modeName: String): LLMModelMode {
        return runCatching { LLMModelMode.valueOf(modeName) }
            .getOrDefault(LLMModelMode.RUN_AUTO)
    }

    companion object {
        private const val TAG = "MelangeModelManager"
    }
}
