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
    fun burn_report_starts_with_dispatcher_style_high_risk_question() {
        val result = dialogueStateManager.handleText("I burned my arm")

        assertEquals("INJURY_REPORT", result.entryIntent)
        assertTrue("BURN" in result.domainHints)
        assertEquals("high_risk_check", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun blistered_burn_routes_to_second_degree_protocol_after_dispatcher_question() {
        val firstTurn = dialogueStateManager.handleText("There are blisters on the arm")
        val secondTurn = dialogueStateManager.handleText("no", firstTurn.dialogueState)

        assertEquals("burn_second_degree_general", secondTurn.protocolId)
        assertEquals("stop_burning_source", secondTurn.stepId)
        assertTrue(secondTurn.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun mild_burn_routes_to_minor_burn_protocol_after_two_negative_answers() {
        val firstTurn = dialogueStateManager.handleText("I burned my arm")
        val secondTurn = dialogueStateManager.handleText("no", firstTurn.dialogueState)
        val thirdTurn = dialogueStateManager.handleText("no", secondTurn.dialogueState)

        assertEquals("burn_minor_general", thirdTurn.protocolId)
        assertEquals("cool_under_running_water", thirdTurn.stepId)
        assertTrue(thirdTurn.dialogueState is DialogueState.ProtocolMode)
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
    fun chest_pain_input_routes_to_chest_pain_protocol_tree() {
        val result = dialogueStateManager.handleText("my father has severe chest pain")

        assertEquals("CHEST_PAIN", result.entryIntent)
        assertEquals("chest_pain_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun seizure_input_routes_to_seizure_protocol_tree() {
        val result = dialogueStateManager.handleText("my friend is having a seizure")

        assertEquals("SEIZURE", result.entryIntent)
        assertEquals("seizure_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun infant_choking_input_starts_with_cough_or_cry_question() {
        val result = dialogueStateManager.handleText("my baby is choking")

        assertEquals("CHOKING", result.entryIntent)
        assertEquals("infant_cough_or_cry_check", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun infant_choking_with_no_cry_routes_to_infant_protocol() {
        val result = dialogueStateManager.handleText("my baby is choking and cannot cry")

        assertEquals("CHOKING", result.entryIntent)
        assertEquals("infant_choking_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun cardiac_arrest_input_routes_to_cpr_protocol() {
        val result = dialogueStateManager.handleText("he is not breathing and has no pulse")

        assertEquals("PERSON_COLLAPSED", result.entryIntent)
        assertEquals("cardiac_arrest_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun adult_choking_starts_with_cough_or_speak_question() {
        val result = dialogueStateManager.handleText("he is choking")

        assertEquals("CHOKING", result.entryIntent)
        assertEquals("cough_or_speak_check", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun generic_bleeding_starts_with_major_bleeding_question() {
        val result = dialogueStateManager.handleText("the wound is bleeding")

        assertEquals("BLEEDING", result.entryIntent)
        assertEquals("major_bleeding_check", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun severe_bleeding_phrase_routes_to_major_bleeding_protocol() {
        val result = dialogueStateManager.handleText("there is blood everywhere")

        assertEquals("bleeding_external_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun minor_bleeding_routes_to_minor_bleeding_protocol_after_no_answer() {
        val firstTurn = dialogueStateManager.handleText("the wound is bleeding")
        val secondTurn = dialogueStateManager.handleText("no", firstTurn.dialogueState)

        assertEquals("bleeding_minor_general", secondTurn.protocolId)
        assertTrue(secondTurn.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun choking_with_effective_cough_routes_to_partial_choking_protocol() {
        val firstTurn = dialogueStateManager.handleText("he is choking")
        val secondTurn = dialogueStateManager.handleText("yes", firstTurn.dialogueState)

        assertEquals("choking_partial_general", secondTurn.protocolId)
        assertTrue(secondTurn.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun breathing_problem_starts_with_red_flag_question() {
        val result = dialogueStateManager.handleText("she is having trouble breathing")

        assertEquals("BREATHING_PROBLEM", result.entryIntent)
        assertEquals("red_flag_check", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun blue_lips_report_routes_to_severe_breathing_protocol() {
        val result = dialogueStateManager.handleText("he has trouble breathing and blue lips")

        assertEquals("breathing_distress_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun seizure_stopped_routes_to_recovery_protocol() {
        val result = dialogueStateManager.handleText("the seizure stopped and now he is breathing")

        assertEquals("seizure_recovery_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun hypoglycemia_starts_with_swallow_question() {
        val result = dialogueStateManager.handleText("he is diabetic and shaky with low blood sugar")

        assertEquals("GENERAL_EMERGENCY", result.entryIntent)
        assertEquals("can_swallow_check", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun unresponsive_hypoglycemia_routes_to_severe_protocol() {
        val result = dialogueStateManager.handleText("unresponsive diabetic with low blood sugar")

        assertEquals("hypoglycemia_unresponsive_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun poisoning_starts_with_exposure_type_question() {
        val result = dialogueStateManager.handleText("there was bleach poisoning")

        assertEquals("POISONING", result.entryIntent)
        assertEquals("skin_or_eye_contact_check", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun contact_poisoning_routes_to_contact_protocol() {
        val result = dialogueStateManager.handleText("chemical in the eye poisoning")

        assertEquals("poisoning_contact_general", result.protocolId)
        assertTrue(result.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun drowning_starts_with_breathing_question() {
        val result = dialogueStateManager.handleText("she nearly drowned in the pool")

        assertEquals("GENERAL_EMERGENCY", result.entryIntent)
        assertEquals("breathing_after_rescue_check", result.currentNodeId)
        assertTrue(result.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun not_breathing_after_drowning_routes_to_drowning_cpr_protocol() {
        val firstTurn = dialogueStateManager.handleText("she nearly drowned in the pool")
        val secondTurn = dialogueStateManager.handleText("no", firstTurn.dialogueState)

        assertEquals("drowning_cpr_general", secondTurn.protocolId)
        assertTrue(secondTurn.dialogueState is DialogueState.ProtocolMode)
    }

    @Test
    fun entry_responses_do_not_leak_into_the_next_question() {
        val firstTurn = dialogueStateManager.handleText("my friend collapsed")
        val secondTurn = dialogueStateManager.handleText("yes", firstTurn.dialogueState)
        val thirdTurn = dialogueStateManager.handleText("no", secondTurn.dialogueState)

        assertEquals("breathing_check", thirdTurn.currentNodeId)
        assertTrue(thirdTurn.dialogueState is DialogueState.EntryMode)
    }

    @Test
    fun newly_added_emergency_intents_route_to_their_protocols() {
        val cases = listOf(
            "his face is drooping and his speech is slurred" to "stroke_fast_general",
            "she has an allergic reaction and a swollen tongue" to "anaphylaxis_general",
            "she got an electric shock from a wire" to "electric_shock_general",
            "he may have a broken wrist fracture" to "fracture_suspected_general",
        )

        for ((input, expectedProtocolId) in cases) {
            val result = dialogueStateManager.handleText(input)
            assertEquals("Input '$input' routed incorrectly", expectedProtocolId, result.protocolId)
            assertTrue("Input '$input' did not enter protocol mode", result.dialogueState is DialogueState.ProtocolMode)
        }
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
