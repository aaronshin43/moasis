package com.example.moasis

import com.example.moasis.data.protocol.FileSystemAssetTextSource
import com.example.moasis.data.protocol.JsonProtocolDataSource
import com.example.moasis.data.protocol.ProtocolRepository
import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.state.ControlIntent
import com.example.moasis.domain.state.DialogueStateManager
import com.example.moasis.domain.state.InterruptionRouter
import com.example.moasis.domain.state.InterruptionType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicEngineTest {
    private val protocolRepository = ProtocolRepository(
        dataSource = JsonProtocolDataSource(
            assetTextSource = FileSystemAssetTextSource(findAssetRoot()),
        ),
    )

    private val dialogueStateManager = DialogueStateManager(protocolRepository)
    private val interruptionRouter = InterruptionRouter()

    @Test
    fun blisters_on_arm_enters_burn_tree_and_resolves_current_first_step() {
        val result = dialogueStateManager.handleText("There are blisters on the arm")

        assertEquals("INJURY_REPORT", result.entryIntent)
        assertTrue("BURN" in result.domainHints)
        assertEquals("burn_second_degree_general", result.protocolId)
        assertEquals("stop_burning_source", result.stepId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun collapsed_person_input_enters_scene_safe_node() {
        val result = dialogueStateManager.handleText("my friend collapsed")

        assertEquals("PERSON_COLLAPSED", result.entryIntent)
        assertEquals("scene_safe", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun life_threat_report_forces_retriage() {
        val currentState = DialogueState.ProtocolMode(
            scenarioId = "burn",
            protocolId = "burn_second_degree_general",
            stepIndex = 0,
            slots = mapOf("location" to "arm"),
            isSpeaking = true,
        )

        val result = dialogueStateManager.handleText("they can't breathe", currentState)

        assertEquals(InterruptionType.STATE_CHANGING_REPORT, result.interruptionDecision?.type)
        assertTrue(result.dialogueState is DialogueState.ReTriageMode)
    }

    @Test
    fun clarification_question_preserves_current_step_index() {
        val currentState = DialogueState.ProtocolMode(
            scenarioId = "burn",
            protocolId = "burn_second_degree_general",
            stepIndex = 0,
            slots = mapOf("location" to "arm"),
            isSpeaking = true,
        )

        val result = dialogueStateManager.handleText("can I use ice?", currentState)

        assertEquals(InterruptionType.CLARIFICATION_QUESTION, result.interruptionDecision?.type)
        val questionState = result.dialogueState as DialogueState.QuestionMode
        assertEquals(0, questionState.returnToStepIndex)
    }

    @Test
    fun next_advances_to_next_protocol_step() {
        val currentState = DialogueState.ProtocolMode(
            scenarioId = "burn",
            protocolId = "burn_second_degree_general",
            stepIndex = 0,
            slots = mapOf("location" to "arm"),
            isSpeaking = false,
        )

        val result = dialogueStateManager.handleText("next", currentState)

        assertEquals(InterruptionType.CONTROL_INTENT, result.interruptionDecision?.type)
        assertEquals(ControlIntent.NEXT, result.interruptionDecision?.controlIntent)
        val protocolState = result.dialogueState as DialogueState.ProtocolMode
        assertEquals(1, protocolState.stepIndex)
    }

    @Test
    fun interruption_router_prioritizes_life_threat_before_control() {
        val decision = interruptionRouter.classify("they can't breathe")

        assertEquals(InterruptionType.STATE_CHANGING_REPORT, decision.type)
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
