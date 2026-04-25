package com.example.moasis.domain.state

import com.example.moasis.domain.model.DialogueState
import com.example.moasis.domain.model.TurnContext
import com.example.moasis.domain.model.UserTurn
import com.example.moasis.domain.model.VisionTaskType

class VisionTaskRouter {
    fun route(turn: UserTurn, context: TurnContext): VisionTaskType {
        val text = buildString {
            append(turn.text.orEmpty())
            append(' ')
            append(turn.voiceTranscript.orEmpty())
        }.trim().lowercase()

        return when {
            context.currentProtocolId == "first_aid_kit_inventory" -> VisionTaskType.KIT_DETECTION
            !context.expectedVisualCheck.isNullOrBlank() -> VisionTaskType.STEP_VERIFICATION
            context.dialogueState is DialogueState.EntryMode &&
                text.containsAny("burn", "blister", "bleeding", "arm", "hand", "face", "wound") ->
                VisionTaskType.INJURY_OBSERVATION

            text.containsAny("look at this", "does this look okay", "what should i do", "based on this photo") ->
                VisionTaskType.GENERAL_MULTIMODAL_QA

            else -> VisionTaskType.GENERAL_MULTIMODAL_QA
        }
    }

    private fun String.containsAny(vararg values: String): Boolean {
        return values.any { contains(it) }
    }
}
