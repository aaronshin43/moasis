package com.example.moasis

import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.TurnContext
import com.example.moasis.domain.model.UserTurn
import com.example.moasis.domain.model.VisionTaskType
import com.example.moasis.domain.state.VisionTaskRouter
import org.junit.Assert.assertEquals
import org.junit.Test

class VisionTaskRouterTest {
    private val router = VisionTaskRouter()

    private fun turn(text: String? = null, voice: String? = null) = UserTurn(
        text = text,
        voiceTranscript = voice,
        imageUris = listOf("content://fake/image.jpg"),
        timestamp = System.currentTimeMillis(),
    )

    // --- KIT_DETECTION ---

    @Test
    fun first_aid_kit_protocol_routes_to_kit_detection() {
        val context = TurnContext(currentProtocolId = "first_aid_kit_inventory")
        val result = router.route(turn(text = "what do I have here"), context)
        assertEquals(VisionTaskType.KIT_DETECTION, result)
    }

    // --- STEP_VERIFICATION ---

    @Test
    fun expected_visual_check_routes_to_step_verification() {
        val context = TurnContext(
            currentProtocolId = "burn_second_degree_general",
            expectedVisualCheck = "bandage_wrap",
        )
        val result = router.route(turn(text = "did I wrap this right"), context)
        assertEquals(VisionTaskType.STEP_VERIFICATION, result)
    }

    @Test
    fun non_empty_expected_visual_check_always_routes_to_step_verification() {
        val context = TurnContext(expectedVisualCheck = "any_check")
        val result = router.route(turn(text = "look at this"), context)
        assertEquals(VisionTaskType.STEP_VERIFICATION, result)
    }

    // --- INJURY_OBSERVATION ---

    @Test
    fun entry_mode_with_burn_keyword_routes_to_injury_observation() {
        val context = TurnContext(
            dialogueState = DialogueState.EntryMode(
                treeId = "burn_tree",
                nodeId = "start",
                slots = emptyMap(),
            ),
        )
        val result = router.route(turn(text = "I have a burn"), context)
        assertEquals(VisionTaskType.INJURY_OBSERVATION, result)
    }

    @Test
    fun entry_mode_with_blister_routes_to_injury_observation() {
        val context = TurnContext(
            dialogueState = DialogueState.EntryMode(
                treeId = "entry",
                nodeId = "start",
                slots = emptyMap(),
            ),
        )
        val result = router.route(turn(text = "there are blisters"), context)
        assertEquals(VisionTaskType.INJURY_OBSERVATION, result)
    }

    @Test
    fun entry_mode_with_bleeding_routes_to_injury_observation() {
        val context = TurnContext(
            dialogueState = DialogueState.EntryMode(
                treeId = "entry",
                nodeId = "start",
                slots = emptyMap(),
            ),
        )
        val result = router.route(turn(text = "bleeding from the arm"), context)
        assertEquals(VisionTaskType.INJURY_OBSERVATION, result)
    }

    @Test
    fun entry_mode_with_wound_routes_to_injury_observation() {
        val context = TurnContext(
            dialogueState = DialogueState.EntryMode(
                treeId = "entry",
                nodeId = "start",
                slots = emptyMap(),
            ),
        )
        val result = router.route(turn(text = "there is a wound on my hand"), context)
        assertEquals(VisionTaskType.INJURY_OBSERVATION, result)
    }

    @Test
    fun entry_mode_without_injury_keyword_falls_to_general() {
        val context = TurnContext(
            dialogueState = DialogueState.EntryMode(
                treeId = "entry",
                nodeId = "start",
                slots = emptyMap(),
            ),
        )
        val result = router.route(turn(text = "look at this photo"), context)
        assertEquals(VisionTaskType.GENERAL_MULTIMODAL_QA, result)
    }

    // --- GENERAL_MULTIMODAL_QA ---

    @Test
    fun look_at_this_without_entry_mode_routes_to_general_qa() {
        val context = TurnContext(
            dialogueState = DialogueState.ProtocolMode(
                scenarioId = "burn",
                protocolId = "burn_second_degree_general",
                stepIndex = 0,
                slots = emptyMap(),
                isSpeaking = false,
            ),
        )
        val result = router.route(turn(text = "look at this"), context)
        assertEquals(VisionTaskType.GENERAL_MULTIMODAL_QA, result)
    }

    @Test
    fun does_this_look_okay_routes_to_general_qa() {
        val context = TurnContext()
        val result = router.route(turn(text = "does this look okay"), context)
        assertEquals(VisionTaskType.GENERAL_MULTIMODAL_QA, result)
    }

    @Test
    fun fallback_routes_to_general_qa() {
        val context = TurnContext()
        val result = router.route(turn(text = "here is a photo"), context)
        assertEquals(VisionTaskType.GENERAL_MULTIMODAL_QA, result)
    }

    // --- Voice transcript used ---

    @Test
    fun voice_transcript_is_considered_for_routing() {
        val context = TurnContext(
            dialogueState = DialogueState.EntryMode(
                treeId = "entry",
                nodeId = "start",
                slots = emptyMap(),
            ),
        )
        val result = router.route(turn(voice = "I have a burn on my arm"), context)
        assertEquals(VisionTaskType.INJURY_OBSERVATION, result)
    }

    // --- Priority: kit > verification > injury > general ---

    @Test
    fun kit_detection_has_highest_priority() {
        // Even with expectedVisualCheck set, kit protocol wins
        val context = TurnContext(
            currentProtocolId = "first_aid_kit_inventory",
            expectedVisualCheck = "bandage_wrap",
        )
        val result = router.route(turn(text = "burn on arm"), context)
        assertEquals(VisionTaskType.KIT_DETECTION, result)
    }

    @Test
    fun step_verification_beats_injury_observation() {
        val context = TurnContext(
            dialogueState = DialogueState.EntryMode(
                treeId = "burn_tree",
                nodeId = "start",
                slots = emptyMap(),
            ),
            expectedVisualCheck = "bandage_check",
        )
        val result = router.route(turn(text = "burn on my arm"), context)
        assertEquals(VisionTaskType.STEP_VERIFICATION, result)
    }
}
