package com.example.moasis

import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.moasis.ai.melange.MelangeLlmEngine
import com.example.moasis.ai.melange.MelangeEmbeddingModelManager
import com.example.moasis.ai.melange.MelangeEmbeddingRuntimeConfig
import com.example.moasis.ai.melange.MelangeModelManager
import com.example.moasis.ai.melange.MelangeSentenceEmbedder
import com.example.moasis.ai.melange.MelangeRuntimeConfig
import com.example.moasis.ai.melange.MelangeVisionModelManager
import com.example.moasis.ai.melange.MelangeVisionRuntimeConfig
import com.example.moasis.ai.melange.MelangeYoloDetectionEngine
import com.example.moasis.ai.melange.RuleBasedLlmEngine
import com.example.moasis.ai.orchestrator.InferenceOrchestrator
import com.example.moasis.ai.prompt.PromptFactory
import com.example.moasis.data.local.AppDatabase
import com.example.moasis.data.local.SessionRepository
import com.example.moasis.data.protocol.AndroidAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.AssetCatalogDataSource
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.nlu.EmbeddingNearestLabelClassifier
import com.example.moasis.domain.nlu.NluRouter
import com.example.moasis.domain.nlu.RegexIntentMatcher
import com.example.moasis.domain.safety.KeywordResponseValidator
import com.example.moasis.domain.state.DialogueStateManager
import com.example.moasis.domain.usecase.AnswerQuestionUseCase
import com.example.moasis.presentation.EmbeddingPreparationStateHolder
import com.example.moasis.presentation.EmergencyViewModelFactory
import com.example.moasis.ui.EmergencyApp
import com.example.moasis.ui.theme.MoasisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var melangeModelManager: MelangeModelManager? = null
    private var melangeEmbeddingModelManager: MelangeEmbeddingModelManager? = null
    private var melangeVisionModelManager: MelangeVisionModelManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Moasis)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val assetTextSource = AndroidAssetTextSource(assets)
        val protocolRepository = ProtocolRepository(
            dataSource = JsonProtocolDataSource(assetTextSource),
        )
        val visualAssetRepository = VisualAssetRepository(
            protocolRepository = protocolRepository,
            assetCatalogDataSource = AssetCatalogDataSource(assetTextSource),
        )
        val melangeConfig = MelangeRuntimeConfig(
            personalKey = BuildConfig.MELANGE_PERSONAL_KEY,
            modelName = BuildConfig.MELANGE_MODEL_NAME,
            modelVersion = BuildConfig.MELANGE_MODEL_VERSION.takeIf { it >= 0 },
            modelModeName = BuildConfig.MELANGE_MODEL_MODE,
            targetName = BuildConfig.MELANGE_TARGET,
            quantTypeName = BuildConfig.MELANGE_QUANT_TYPE,
            apTypeName = resolveMelangeApType(BuildConfig.MELANGE_AP_TYPE),
        )
        val aiEnabled = BuildConfig.AI_ENABLED && melangeConfig.isConfigured
        val embeddingConfig = MelangeEmbeddingRuntimeConfig(
            personalKey = BuildConfig.EMBEDDING_PERSONAL_KEY,
            modelName = BuildConfig.EMBEDDING_MODEL_NAME,
            modelVersion = BuildConfig.EMBEDDING_MODEL_VERSION.takeIf { it >= 0 },
            modelModeName = BuildConfig.EMBEDDING_MODEL_MODE,
        )
        val embeddingEnabled = BuildConfig.EMBEDDING_ENABLED && embeddingConfig.isConfigured
        val embeddingPreparationStateHolder = EmbeddingPreparationStateHolder().also { holder ->
            if (embeddingEnabled) {
                val initialStatus = if (aiEnabled) {
                    "Waiting for the main AI model before preparing the embedding model."
                } else {
                    "Preparing local embedding model."
                }
                holder.markPreparing(initialStatus)
            } else {
                holder.markDisabled()
            }
        }
        val visionConfig = MelangeVisionRuntimeConfig(
            personalKey = BuildConfig.VISION_PERSONAL_KEY,
            modelName = BuildConfig.VISION_MODEL_NAME,
            modelVersion = BuildConfig.VISION_MODEL_VERSION.takeIf { it >= 0 },
            modelModeName = BuildConfig.VISION_MODEL_MODE,
            quantTypeName = BuildConfig.VISION_QUANT_TYPE.takeIf { it.isNotBlank() },
            targetName = BuildConfig.VISION_TARGET.takeIf { it.isNotBlank() },
            apTypeName = resolveVisionApType(BuildConfig.VISION_AP_TYPE),
        )
        val visionEnabled = BuildConfig.VISION_ENABLED && visionConfig.isConfigured
        Log.d(
            TAG,
            "Embedding classifier enabled=$embeddingEnabled model=${embeddingConfig.modelName} version=${embeddingConfig.modelVersion ?: "latest"} mode=${embeddingConfig.modelModeName}",
        )
        if (visionEnabled) {
            Log.d(
                TAG,
                "YOLO detector enabled=true model=${visionConfig.modelName} version=${visionConfig.modelVersion ?: "latest"} mode=${visionConfig.modelModeName} target=${visionConfig.targetName ?: "auto"} ap=${visionConfig.apTypeName ?: "auto"} quant=${visionConfig.quantTypeName ?: "auto"}",
            )
        }
        val intentClassifier = if (embeddingEnabled) {
            val embeddingModelManager = MelangeEmbeddingModelManager(
                context = applicationContext,
                config = embeddingConfig,
            )
            melangeEmbeddingModelManager = embeddingModelManager
            EmbeddingNearestLabelClassifier(
                embedder = MelangeSentenceEmbedder(
                    assetManager = assets,
                    modelManager = embeddingModelManager,
                ),
                fallbackMatcher = RegexIntentMatcher(),
            )
        } else {
            RegexIntentMatcher()
        }
        val llmEngine = if (aiEnabled) {
            val modelManager = MelangeModelManager(
                context = applicationContext,
                config = melangeConfig,
            )
            melangeModelManager = modelManager
            MelangeLlmEngine(
                modelManager = modelManager,
            )
        } else {
            RuleBasedLlmEngine()
        }
        if (visionEnabled) {
            melangeVisionModelManager = MelangeVisionModelManager(
                context = applicationContext,
                config = visionConfig,
            )
        }
        val visionDetectionEngine = melangeVisionModelManager?.let { MelangeYoloDetectionEngine(it) }
        when {
            aiEnabled && embeddingEnabled -> preloadEmbeddingModelAfterLlmReady(embeddingPreparationStateHolder)
            embeddingEnabled -> preloadEmbeddingModel(embeddingPreparationStateHolder)
        }
        val inferenceOrchestrator = InferenceOrchestrator(
            llmEngine = llmEngine,
            promptFactory = PromptFactory(),
            responseValidator = KeywordResponseValidator(),
        )
        val answerQuestionUseCase = AnswerQuestionUseCase(
            protocolRepository = protocolRepository,
            inferenceOrchestrator = inferenceOrchestrator,
        )
        val sessionRepository = SessionRepository(
            sessionDao = AppDatabase.getInstance(applicationContext).sessionDao(),
        )
        val viewModelFactory = EmergencyViewModelFactory(
            dialogueStateManager = DialogueStateManager(
                protocolRepository = protocolRepository,
                nluRouter = NluRouter(intentClassifier = intentClassifier),
            ),
            protocolRepository = protocolRepository,
            visualAssetRepository = visualAssetRepository,
            inferenceOrchestrator = inferenceOrchestrator,
            answerQuestionUseCase = answerQuestionUseCase,
            sessionRepository = sessionRepository,
            melangeModelManager = melangeModelManager,
            melangeVisionModelManager = melangeVisionModelManager,
            visionDetectionEngine = visionDetectionEngine,
            embeddingPreparationStateHolder = embeddingPreparationStateHolder,
            aiEnabled = aiEnabled,
        )
        setContent {
            MoasisTheme {
                EmergencyApp(
                    modifier = Modifier.fillMaxSize(),
                    factory = viewModelFactory,
                )
            }
        }
    }

    override fun onDestroy() {
        melangeModelManager?.release()
        melangeModelManager = null
        melangeEmbeddingModelManager?.release()
        melangeEmbeddingModelManager = null
        melangeVisionModelManager?.release()
        melangeVisionModelManager = null
        super.onDestroy()
    }

    private fun preloadEmbeddingModel(
        embeddingPreparationStateHolder: EmbeddingPreparationStateHolder,
    ) {
        val modelManager = melangeEmbeddingModelManager ?: return
        if (modelManager.isPreparedInMemory()) {
            Log.d(TAG, "Embedding model ready")
            embeddingPreparationStateHolder.markReady()
            return
        }
        embeddingPreparationStateHolder.markPreparing("Preparing local embedding model.")
        lifecycleScope.launch(Dispatchers.IO) {
            modelManager.getOrCreateSession()
                .onSuccess {
                    Log.d(TAG, "Embedding model ready")
                    embeddingPreparationStateHolder.markReady()
                }
                .onFailure { throwable ->
                    val detail = throwable.message?.takeIf { it.isNotBlank() } ?: throwable::class.java.simpleName
                    Log.w(TAG, "Embedding model prepare failed: $detail")
                    embeddingPreparationStateHolder.markFailed(
                        "Embedding model preparation failed. $detail",
                    )
                }
        }
    }

    private fun preloadEmbeddingModelAfterLlmReady(
        embeddingPreparationStateHolder: EmbeddingPreparationStateHolder,
    ) {
        val embeddingManager = melangeEmbeddingModelManager ?: return
        val llmManager = melangeModelManager ?: run {
            preloadEmbeddingModel(embeddingPreparationStateHolder)
            return
        }
        if (embeddingManager.isPreparedInMemory()) {
            Log.d(TAG, "Embedding model ready")
            embeddingPreparationStateHolder.markReady()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            repeat(120) {
                if (llmManager.isPreparedInMemory()) {
                    Log.d(TAG, "LLM model ready. Starting deferred embedding preload.")
                    embeddingPreparationStateHolder.markPreparing("Preparing local embedding model.")
                    preloadEmbeddingModel(embeddingPreparationStateHolder)
                    return@launch
                }
                delay(500)
            }
            Log.w(TAG, "LLM model was not ready within the wait window. Skipping deferred embedding preload.")
            embeddingPreparationStateHolder.markFailed(
                "Embedding model preparation did not start because the main AI model was not ready in time.",
            )
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private fun resolveMelangeApType(requestedApType: String): String {
        val normalized = requestedApType.trim().uppercase()
        if (normalized != "AUTO") {
            Log.d(TAG, "Using configured Melange APType=$normalized")
            return normalized
        }

        val isQualcomm = sequenceOf(
            Build.SOC_MANUFACTURER,
            Build.HARDWARE,
            Build.BOARD,
            Build.PRODUCT,
        )
            .filterNotNull()
            .map { it.lowercase() }
            .any { value ->
                "qualcomm" in value || "qcom" in value || "snapdragon" in value
            }

        val resolved = if (isQualcomm) "GPU" else "CPU"
        Log.d(TAG, "Resolved Melange APType=$resolved from requested=AUTO")
        return resolved
    }

    private fun resolveVisionApType(requestedApType: String): String? {
        val normalized = requestedApType.trim().uppercase()
        if (normalized.isBlank()) {
            return null
        }
        if (normalized != "AUTO") {
            Log.d(TAG, "Using configured vision APType=$normalized")
            return normalized
        }

        val isQualcomm = sequenceOf(
            Build.SOC_MANUFACTURER,
            Build.HARDWARE,
            Build.BOARD,
            Build.PRODUCT,
        )
            .filterNotNull()
            .map { it.lowercase() }
            .any { value ->
                "qualcomm" in value || "qcom" in value || "snapdragon" in value
            }

        val resolved = if (isQualcomm) "NPU" else "CPU"
        Log.d(TAG, "Resolved vision APType=$resolved from requested=AUTO")
        return resolved
    }
}
