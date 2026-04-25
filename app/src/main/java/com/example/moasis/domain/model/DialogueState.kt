package com.example.moasis.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class DialogueState {
    @Serializable
    @SerialName("entry_mode")
    data class EntryMode(
        val treeId: String,
        val nodeId: String,
        val slots: Map<String, String> = emptyMap(),
        val history: List<String> = emptyList(),
    ) : DialogueState()

    @Serializable
    @SerialName("protocol_mode")
    data class ProtocolMode(
        val scenarioId: String,
        val protocolId: String,
        val stepIndex: Int,
        val slots: Map<String, String> = emptyMap(),
        val isSpeaking: Boolean = false,
        val suspendedByQuestion: Boolean = false,
    ) : DialogueState()

    @Serializable
    @SerialName("question_mode")
    data class QuestionMode(
        val scenarioId: String,
        val protocolId: String,
        val stepIndex: Int,
        val userQuestion: String,
        val returnToStepIndex: Int,
    ) : DialogueState()

    @Serializable
    @SerialName("retriage_mode")
    data class ReTriageMode(
        val previousScenarioId: String? = null,
        val newInput: String,
    ) : DialogueState()

    @Serializable
    @SerialName("completed")
    data object Completed : DialogueState()
}
