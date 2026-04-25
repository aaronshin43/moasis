package com.example.moasis

import com.example.moasis.ai.model.LlmConstraints
import com.example.moasis.ai.model.LlmRequest
import com.example.moasis.ai.model.LlmRequestMode
import com.example.moasis.ai.model.LlmResponse
import com.example.moasis.ai.model.LlmResponseType
import com.example.moasis.ai.model.LlmStyle
import com.example.moasis.ai.model.ResumePolicy
import com.example.moasis.domain.model.AssetRef
import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.DomainIntent
import com.example.moasis.domain.model.EntryIntent
import com.example.moasis.domain.model.FactSource
import com.example.moasis.domain.model.ObservedFact
import com.example.moasis.domain.model.Protocol
import com.example.moasis.domain.model.ProtocolStep
import com.example.moasis.domain.model.Route
import com.example.moasis.domain.model.Transition
import com.example.moasis.domain.model.Tree
import com.example.moasis.domain.model.TreeNode
import com.example.moasis.domain.model.TurnContext
import com.example.moasis.domain.model.UserTurn
import com.example.moasis.domain.model.VisualAid
import com.example.moasis.domain.model.VisualAidType
import com.example.moasis.domain.model.VisionTaskType
import com.example.moasis.presentation.AppEvent
import com.example.moasis.presentation.ChecklistItem
import com.example.moasis.presentation.UiAction
import com.example.moasis.presentation.UiState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreModelSerializationTest {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @Test
    fun core_models_are_serialization_roundtrippable() {
        val userTurn = UserTurn(
            text = "My friend collapsed",
            voiceTranscript = "my friend collapsed",
            imageUris = listOf("content://sample/image"),
            timestamp = 1_713_984_000L,
        )
        assertRoundTrip(userTurn, UserTurn.serializer())

        val dialogueState: DialogueState = DialogueState.ProtocolMode(
            scenarioId = "collapse",
            protocolId = "collapsed_person_entry",
            stepIndex = 1,
            slots = mapOf("responsive" to "no"),
            isSpeaking = true,
            suspendedByQuestion = false,
        )
        assertRoundTrip(dialogueState, DialogueState.serializer())

        val turnContext = TurnContext(
            dialogueState = dialogueState,
            currentProtocolId = "collapsed_person_entry",
            currentStepId = "breathing_check",
            lastAssistantAction = "ask_question",
            expectedVisualCheck = "bandage_wrap",
        )
        assertRoundTrip(turnContext, TurnContext.serializer())

        val observedFact = ObservedFact(
            key = "burn_location",
            value = "arm",
            confidence = 0.82f,
            source = FactSource.USER_REPORTED,
            evidence = "blisters on the arm",
        )
        assertRoundTrip(observedFact, ObservedFact.serializer())

        assertRoundTrip(VisionTaskType.INJURY_OBSERVATION, VisionTaskType.serializer())
        assertRoundTrip(EntryIntent.PERSON_COLLAPSED, EntryIntent.serializer())
        assertRoundTrip(DomainIntent.BURN, DomainIntent.serializer())

        val visualAid = VisualAid(
            assetId = "burn_cool_water_arm_01",
            type = VisualAidType.IMAGE,
            caption = "Cooling example",
            contentDescription = "An arm held under running water",
            priority = 10,
        )
        assertRoundTrip(visualAid, VisualAid.serializer())

        val uiState = UiState(
            title = "Burn care",
            primaryInstruction = "Cool the burn area under running water.",
            secondaryInstruction = "Keep cooling for 10 minutes.",
            warningText = "Do not use ice.",
            checklist = listOf(
                ChecklistItem(
                    id = "cool_water",
                    text = "Use cool running water",
                    isChecked = true,
                )
            ),
            visualAids = listOf(visualAid),
            currentStep = 1,
            totalSteps = 2,
            isListening = false,
            isSpeaking = true,
            showCallEmergencyButton = false,
        )
        assertRoundTrip(uiState, UiState.serializer())

        val assetRef = AssetRef(
            assetId = "burn_cool_water_arm_01",
            type = VisualAidType.IMAGE,
            caption = "Cooling example",
            contentDescription = "An arm held under running water",
            priority = 10,
        )
        assertRoundTrip(assetRef, AssetRef.serializer())

        val protocol = Protocol(
            protocolId = "burn_second_degree_general",
            title = "Second-degree burn basic care",
            category = "burn",
            requiredSlots = listOf("location"),
            safetyFlags = listOf("show_emergency_call_if_face_or_airway"),
            steps = listOf(
                ProtocolStep(
                    stepId = "cool_water",
                    canonicalText = "Cool the burn area under cool running water for at least 10 minutes.",
                    mustKeepKeywords = listOf("running water", "10 minutes"),
                    forbiddenKeywords = listOf("direct ice"),
                    assetRefs = listOf(assetRef),
                )
            ),
        )
        assertRoundTrip(protocol, Protocol.serializer())

        val tree = Tree(
            treeId = "collapsed_person_entry",
            version = "1.0",
            startNode = "scene_safe",
            nodes = listOf(
                TreeNode(
                    id = "scene_safe",
                    type = "question",
                    prompt = "Is the area safe?",
                    slotKey = "scene_safe",
                    transitions = listOf(
                        Transition(condition = "yes", to = "responsive_check"),
                        Transition(condition = "no", to = "safety_instruction"),
                    ),
                ),
                TreeNode(
                    id = "major_symptom_router",
                    type = "router",
                    routes = listOf(
                        Route(condition = "has_massive_bleeding", toTree = "bleeding_tree")
                    ),
                    fallbackTo = "general_assessment_tree",
                ),
            ),
        )
        assertRoundTrip(tree, Tree.serializer())

        val llmRequest = LlmRequest(
            mode = LlmRequestMode.PERSONALIZE_STEP,
            scenarioId = "burn",
            protocolId = "burn_second_degree_general",
            stepId = "cool_water",
            canonicalText = "Cool the burn area under cool running water for at least 10 minutes.",
            slots = mapOf("location" to "arm"),
            constraints = LlmConstraints(
                doNotAddNewSteps = true,
                doNotRemoveRequiredDetails = true,
                keepKeywords = listOf("running water", "10 minutes"),
                forbiddenContent = listOf("use ice directly"),
            ),
            style = LlmStyle(
                tone = "calm",
                length = "short",
                targetListener = "caregiver",
            ),
        )
        assertRoundTrip(llmRequest, LlmRequest.serializer())

        val llmResponse = LlmResponse(
            responseType = LlmResponseType.PERSONALIZED_STEP,
            spokenText = "Cool the burn on the arm under cool running water for at least 10 minutes.",
            summaryText = "Cool with running water for 10 minutes",
            safetyNotes = listOf("Do not apply ice directly."),
            resumePolicy = ResumePolicy.RESUME_SAME_STEP,
        )
        assertRoundTrip(llmResponse, LlmResponse.serializer())

        val appEvent: AppEvent = AppEvent.UserTappedAction(UiAction.SubmitText("I burned my arm"))
        assertRoundTrip(appEvent, AppEvent.serializer())
    }

    private fun <T> assertRoundTrip(value: T, serializer: kotlinx.serialization.KSerializer<T>) {
        val encoded = json.encodeToString(serializer, value)
        val decoded = json.decodeFromString(serializer, encoded)
        assertEquals(value, decoded)
    }
}
