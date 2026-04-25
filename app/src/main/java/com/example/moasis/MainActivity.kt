package com.example.moasis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.moasis.ai.melange.MelangeLlmEngine
import com.example.moasis.ai.melange.MelangeModelManager
import com.example.moasis.ai.melange.MelangeRuntimeConfig
import com.example.moasis.ai.melange.RuleBasedLlmEngine
import com.example.moasis.ai.orchestrator.InferenceOrchestrator
import com.example.moasis.ai.prompt.PromptFactory
import com.example.moasis.data.protocol.AndroidAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.AssetCatalogDataSource
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.safety.KeywordResponseValidator
import com.example.moasis.domain.state.DialogueStateManager
import com.example.moasis.domain.usecase.AnswerQuestionUseCase
import com.example.moasis.presentation.EmergencyViewModelFactory
import com.example.moasis.ui.EmergencyApp
import com.example.moasis.ui.theme.MoasisTheme

class MainActivity : ComponentActivity() {
    private var melangeModelManager: MelangeModelManager? = null

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
        )
        val aiEnabled = BuildConfig.AI_ENABLED && melangeConfig.isConfigured
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
        val inferenceOrchestrator = InferenceOrchestrator(
            llmEngine = llmEngine,
            promptFactory = PromptFactory(),
            responseValidator = KeywordResponseValidator(),
        )
        val answerQuestionUseCase = AnswerQuestionUseCase(
            protocolRepository = protocolRepository,
            inferenceOrchestrator = inferenceOrchestrator,
        )
        val viewModelFactory = EmergencyViewModelFactory(
            dialogueStateManager = DialogueStateManager(protocolRepository),
            protocolRepository = protocolRepository,
            visualAssetRepository = visualAssetRepository,
            inferenceOrchestrator = inferenceOrchestrator,
            answerQuestionUseCase = answerQuestionUseCase,
            melangeModelManager = melangeModelManager,
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
        super.onDestroy()
    }
}
