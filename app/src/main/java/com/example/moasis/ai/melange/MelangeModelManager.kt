package com.example.moasis.ai.melange

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.zeticai.mlange.core.model.APType
import com.zeticai.mlange.core.model.ModelLoadingStatus
import com.zeticai.mlange.core.model.llm.LLMQuantType
import com.zeticai.mlange.core.model.llm.LLMTarget
import com.zeticai.mlange.core.model.llm.ZeticMLangeLLMModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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

    private val cacheRoot: File
        get() = File(appContext.filesDir, "mlange_cache")

    // fun isConfigured(): Boolean = config.isConfigured

    fun isPreparedInMemory(): Boolean = cachedModel != null

    fun isLikelySupportedAbi(): Boolean {
        return Build.SUPPORTED_ABIS.any { abi -> abi.startsWith("arm64") || abi.startsWith("armeabi") }
    }

    fun hasInternetConnection(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun configuredModelLabel(): String {
        val versionSuffix = config.modelVersion?.let { " v$it" }.orEmpty()
        return "${config.modelName}$versionSuffix (${config.targetName}/${config.quantTypeName}/${config.apTypeName})"
    }

    fun configuredModelName(): String = config.modelName

    fun inspectCache(): AiCacheSnapshot {
        val llmRoot = cacheRoot.resolve("llm")
        val llmDirs = llmRoot.listFiles { file -> file.isDirectory }.orEmpty()
        val projectFiles = llmRoot.walkTopDown()
            .filter { it.isFile && it.name == "project.ztcl" }
            .count()
        val targetFiles = llmRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "gguf" || it.name.contains("LLAMA_CPP", ignoreCase = true)) }
            .count()
        return AiCacheSnapshot(
            rootPath = cacheRoot.absolutePath,
            exists = cacheRoot.exists(),
            llmCacheDirectories = llmDirs.size,
            projectFiles = projectFiles,
            targetFiles = targetFiles,
        )
    }

    fun getOrCreateModel(
        onProgress: ((Float) -> Unit)? = null,
        onStatusChanged: ((ModelLoadingStatus) -> Unit)? = null,
        onDownloadedBytes: ((Long) -> Unit)? = null,
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
                MelangeInitCoordinator.runExclusive(
                    modelType = "llm",
                    modelName = config.modelName,
                ) {
                    withDownloadedBytes(onBytes = onDownloadedBytes) {
                        ZeticMLangeLLMModel(
                            context = appContext,
                            personalKey = config.personalKey,
                            name = config.modelName,
                            version = config.modelVersion,
                            target = resolveTarget(config.targetName),
                            quantType = resolveQuantType(config.quantTypeName),
                            apType = resolveApType(config.apTypeName),
                            onProgress = { progress ->
                                lastProgress = progress
                                onProgress?.invoke(progress)
                                Log.d(TAG, "Melange init progress=${"%.2f".format(progress)}")
                            },
                            onStatusChanged = { status ->
                                onStatusChanged?.invoke(status)
                                Log.d(TAG, "Melange status=$status")
                            },
                        )
                    }
                }
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

    private fun resolveTarget(targetName: String): LLMTarget {
        return runCatching { LLMTarget.valueOf(targetName) }
            .getOrDefault(LLMTarget.LLAMA_CPP)
    }

    private fun resolveQuantType(quantTypeName: String): LLMQuantType {
        return runCatching { LLMQuantType.valueOf(quantTypeName) }
            .getOrDefault(LLMQuantType.GGUF_QUANT_Q4_K_M)
    }

    private fun resolveApType(apTypeName: String): APType {
        return runCatching { APType.valueOf(apTypeName) }
            .getOrDefault(APType.CPU)
    }

    companion object {
        private const val TAG = "MelangeModelManager"
    }

    private fun <T> withDownloadedBytes(
        onBytes: ((Long) -> Unit)?,
        block: () -> T,
    ): T {
        if (onBytes == null) {
            return block()
        }
        val baseline = cacheRoot.sizeRecursivelyOrZero()
        val running = AtomicBoolean(true)
        val poller = Thread {
            while (running.get()) {
                onBytes((cacheRoot.sizeRecursivelyOrZero() - baseline).coerceAtLeast(0))
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        try {
            return block()
        } finally {
            running.set(false)
            poller.interrupt()
            onBytes((cacheRoot.sizeRecursivelyOrZero() - baseline).coerceAtLeast(0))
        }
    }
}

data class AiCacheSnapshot(
    val rootPath: String,
    val exists: Boolean,
    val llmCacheDirectories: Int,
    val projectFiles: Int,
    val targetFiles: Int,
) {
    fun summaryText(): String {
        return if (!exists) {
            "Cache: missing"
        } else {
            "Cache: llm_dirs=$llmCacheDirectories, project=$projectFiles, targets=$targetFiles"
        }
    }
}

private fun File.sizeRecursivelyOrZero(): Long =
    if (!exists()) 0L else walkTopDown().filter { it.isFile }.sumOf { it.length() }
