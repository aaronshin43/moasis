package com.example.moasis

import com.example.moasis.ai.melange.RuleBasedLlmEngine
import com.example.moasis.ai.model.LlmRequest
import com.example.moasis.ai.model.LlmRequestMode
import com.example.moasis.ai.model.LlmResponse
import com.example.moasis.ai.model.LlmResponseType
import com.example.moasis.ai.model.OnDeviceLlmEngine
import com.example.moasis.ai.model.ResumePolicy
import com.example.moasis.ai.orchestrator.InferenceOrchestrator
import com.example.moasis.ai.prompt.PromptFactory
import com.example.moasis.data.protocol.FileSystemAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.AssetCatalogDataSource
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.safety.KeywordResponseValidator
import com.example.moasis.domain.usecase.AnswerQuestionUseCase
import com.example.moasis.presentation.EmergencyViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiIntegrationTest {
    private val assetSource = FileSystemAssetTextSource(findAssetRoot())
    private val protocolRepository = ProtocolRepository(JsonProtocolDataSource(assetSource))
    private val protocol = requireNotNull(protocolRepository.getProtocol("burn_second_degree_general"))
    private val step = requireNotNull(protocol.steps.firstOrNull { it.stepId == "cool_water" })
    private val firstStep = requireNotNull(protocol.steps.firstOrNull())
    private val promptFactory = PromptFactory()
    private val validator = KeywordResponseValidator()

    @Test
    fun same_step_reads_differently_for_caregiver_and_child() {
        val engine = RuleBasedLlmEngine()
        val orchestrator = InferenceOrchestrator(engine, promptFactory, validator)

        val caregiver = orchestrator.personalizeStep(
            scenarioId = "burn",
            protocol = protocol,
            step = step,
            slots = mapOf("location" to "arm"),
            targetListener = "caregiver",
        )
        val child = orchestrator.personalizeStep(
            scenarioId = "burn",
            protocol = protocol,
            step = step,
            slots = mapOf("location" to "arm"),
            targetListener = "child",
        )

        assertNotEquals(caregiver.spokenText, child.spokenText)
        assertTrue(caregiver.spokenText.contains("running water", ignoreCase = true))
        assertTrue(child.spokenText.contains("running water", ignoreCase = true))
    }

    @Test
    fun validator_missing_required_keyword_falls_back_to_canonical_text() {
        val validation = validator.validate(
            canonicalText = step.canonicalText,
            responseText = "Cool the burn for a while.",
            mustKeepKeywords = step.mustKeepKeywords,
            forbiddenKeywords = step.forbiddenKeywords,
        )

        assertEquals(false, validation.isValid)
        assertEquals(step.canonicalText, validation.resolvedText)
    }

    @Test
    fun answer_question_returns_resume_text_with_step_action() {
        val useCase = AnswerQuestionUseCase(
            protocolRepository = protocolRepository,
            inferenceOrchestrator = InferenceOrchestrator(
                llmEngine = RuleBasedLlmEngine(),
                promptFactory = promptFactory,
                responseValidator = validator,
            ),
        )

        val result = useCase.answer(
            scenarioId = "burn",
            protocolId = protocol.protocolId,
            stepIndex = protocol.steps.indexOfFirst { it.stepId == "cool_water" },
            userQuestion = "Can I use ice?",
        )

        assertTrue(result.answerText.contains("ice", ignoreCase = true))
        assertTrue(result.resumeText.contains("Cool the burn area", ignoreCase = true))
        assertTrue(result.resumeText.contains("running water", ignoreCase = true))
    }

    @Test
    fun llm_timeout_falls_back_to_canonical_text() {
        val orchestrator = InferenceOrchestrator(
            llmEngine = RuleBasedLlmEngine(shouldTimeout = true),
            promptFactory = promptFactory,
            responseValidator = validator,
        )

        val result = orchestrator.personalizeStep(
            scenarioId = "burn",
            protocol = protocol,
            step = step,
            slots = mapOf("location" to "arm"),
            targetListener = "caregiver",
        )

        assertEquals(step.canonicalText, result.spokenText)
        assertEquals(true, result.usedFallback)
    }

    @Test
    fun ai_disabled_view_model_keeps_canonical_step_text() {
        val visualAssetRepository = VisualAssetRepository(
            protocolRepository = protocolRepository,
            assetCatalogDataSource = AssetCatalogDataSource(assetSource),
        )
        val viewModel = EmergencyViewModel(
            dialogueStateManager = com.example.moasis.domain.state.DialogueStateManager(protocolRepository),
            protocolRepository = protocolRepository,
            visualAssetRepository = visualAssetRepository,
            inferenceOrchestrator = InferenceOrchestrator(
                llmEngine = RuleBasedLlmEngine(),
                promptFactory = promptFactory,
                responseValidator = validator,
            ),
            answerQuestionUseCase = AnswerQuestionUseCase(
                protocolRepository = protocolRepository,
                inferenceOrchestrator = InferenceOrchestrator(
                    llmEngine = RuleBasedLlmEngine(),
                    promptFactory = promptFactory,
                    responseValidator = validator,
                ),
            ),
            aiEnabled = false,
        )

        viewModel.startEmergency("there are blisters on my arm")
        viewModel.submitText("no")

        assertEquals(firstStep.canonicalText, viewModel.viewState.value.uiState.primaryInstruction)
    }

    private fun findAssetRoot(): File {
        var current = File(".").absoluteFile
        repeat(6) {
            val candidate = File(current, "app/src/main/assets")
            if (candidate.exists()) {
                return candidate
            }
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate app/src/main/assets from test working directory.")
    }
}
