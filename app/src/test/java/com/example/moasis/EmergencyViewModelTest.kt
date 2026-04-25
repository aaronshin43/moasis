package com.example.moasis

import com.example.moasis.ai.melange.RuleBasedLlmEngine
import com.example.moasis.ai.orchestrator.InferenceOrchestrator
import com.example.moasis.ai.prompt.PromptFactory
import com.example.moasis.data.protocol.FileSystemAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.data.visual.AssetCatalogDataSource
import com.example.moasis.data.visual.VisualAssetRepository
import com.example.moasis.domain.safety.KeywordResponseValidator
import com.example.moasis.domain.state.DialogueStateManager
import com.example.moasis.domain.usecase.AnswerQuestionUseCase
import com.example.moasis.presentation.EmergencyViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyViewModelTest {
    private val assetSource = FileSystemAssetTextSource(findAssetRoot())
    private val protocolRepository = ProtocolRepository(JsonProtocolDataSource(assetSource))
    private val visualAssetRepository = VisualAssetRepository(
        protocolRepository = protocolRepository,
        assetCatalogDataSource = AssetCatalogDataSource(assetSource),
    )
    private val viewModel = EmergencyViewModel(
        dialogueStateManager = DialogueStateManager(protocolRepository),
        protocolRepository = protocolRepository,
        visualAssetRepository = visualAssetRepository,
        inferenceOrchestrator = InferenceOrchestrator(
            llmEngine = RuleBasedLlmEngine(),
            promptFactory = PromptFactory(),
            responseValidator = KeywordResponseValidator(),
        ),
        answerQuestionUseCase = AnswerQuestionUseCase(
            protocolRepository = protocolRepository,
            inferenceOrchestrator = InferenceOrchestrator(
                llmEngine = RuleBasedLlmEngine(),
                promptFactory = PromptFactory(),
                responseValidator = KeywordResponseValidator(),
            ),
        ),
        aiEnabled = false,
    )

    @Test
    fun burn_input_starts_dispatcher_style_question_flow() {
        viewModel.startEmergency("I burned my arm")

        val state = viewModel.viewState.value
        assertEquals("Burn Triage", state.uiState.title)
        assertEquals(
            "Start cooling the burn with cool running water now if you can do it safely. Is the burn on the face, hands, feet, or genitals, over a large area, charred, or affecting breathing?",
            state.uiState.primaryInstruction,
        )
        assertEquals(listOf("Yes", "No"), state.quickResponses)
    }

    @Test
    fun burn_answers_route_into_second_degree_protocol() {
        viewModel.startEmergency("there are blisters on my arm")
        viewModel.submitText("no")

        val state = viewModel.viewState.value
        assertEquals("Second-degree burn basic care", state.uiState.title)
        assertEquals(
            "Move the person away from the heat source. If clothing is on fire, have them stop, drop, and roll, or smother the flames with a blanket.",
            state.uiState.primaryInstruction,
        )
        assertEquals(1, state.uiState.currentStep)
        assertEquals(6, state.uiState.totalSteps)
    }

    @Test
    fun collapsed_input_shows_scene_safe_question_with_yes_no_actions() {
        viewModel.startEmergency("my friend collapsed")

        val state = viewModel.viewState.value
        assertEquals("Collapsed Person Triage", state.uiState.title)
        assertEquals(
            "Is the area safe? If not, move away from immediate danger and make the area as safe as you can before helping.",
            state.uiState.primaryInstruction,
        )
        assertEquals(listOf("Yes", "No"), state.quickResponses)
    }

    @Test
    fun collapsed_yes_no_path_branches_to_breathing_check() {
        viewModel.startEmergency("my friend collapsed")
        viewModel.submitText("yes")
        viewModel.submitText("no")

        val state = viewModel.viewState.value
        assertEquals("Collapsed Person Triage", state.uiState.title)
        assertEquals("Are they breathing normally? Treat gasping as no.", state.uiState.primaryInstruction)
    }

    @Test
    fun state_changing_report_switches_to_retriage_ui() {
        viewModel.startEmergency("my father has severe chest pain")
        viewModel.submitText("they can't breathe")

        val state = viewModel.viewState.value
        assertEquals("Re-triage required", state.uiState.title)
        assertTrue((state.statusText ?: "").contains("higher-priority", ignoreCase = true))
    }

    @Test
    fun image_only_submission_keeps_current_flow_and_shows_attachment_status() {
        viewModel.startEmergency("my father has severe chest pain")
        viewModel.attachImage("C:/tmp/example.jpg")
        viewModel.submitTurn()

        val state = viewModel.viewState.value
        assertEquals("Suspected heart attack (chest pain)", state.uiState.title)
        assertEquals(listOf("C:/tmp/example.jpg"), state.attachedImagePaths)
        assertTrue((state.statusText ?: "").contains("Image attached", ignoreCase = true))
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
