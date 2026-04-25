package com.example.moasis.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LlmRequest(
    val mode: LlmRequestMode,
    @SerialName("scenario_id")
    val scenarioId: String,
    @SerialName("protocol_id")
    val protocolId: String,
    @SerialName("step_id")
    val stepId: String? = null,
    @SerialName("current_step_id")
    val currentStepId: String? = null,
    @SerialName("canonical_text")
    val canonicalText: String,
    val slots: Map<String, String> = emptyMap(),
    val constraints: LlmConstraints = LlmConstraints(),
    val style: LlmStyle? = null,
    @SerialName("user_question")
    val userQuestion: String? = null,
    @SerialName("known_prohibitions")
    val knownProhibitions: List<String> = emptyList(),
)

@Serializable
enum class LlmRequestMode {
    @SerialName("personalize_step")
    PERSONALIZE_STEP,

    @SerialName("answer_question")
    ANSWER_QUESTION,
}

@Serializable
data class LlmConstraints(
    @SerialName("do_not_add_new_steps")
    val doNotAddNewSteps: Boolean = false,
    @SerialName("do_not_remove_required_details")
    val doNotRemoveRequiredDetails: Boolean = false,
    @SerialName("keep_keywords")
    val keepKeywords: List<String> = emptyList(),
    @SerialName("forbidden_content")
    val forbiddenContent: List<String> = emptyList(),
    @SerialName("answer_only_within_current_context")
    val answerOnlyWithinCurrentContext: Boolean = false,
    @SerialName("do_not_change_protocol_order")
    val doNotChangeProtocolOrder: Boolean = false,
    @SerialName("do_not_make_new_diagnosis")
    val doNotMakeNewDiagnosis: Boolean = false,
)

@Serializable
data class LlmStyle(
    val tone: String,
    val length: String,
    @SerialName("target_listener")
    val targetListener: String,
)
