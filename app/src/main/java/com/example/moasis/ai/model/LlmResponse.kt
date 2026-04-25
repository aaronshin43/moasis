package com.example.moasis.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LlmResponse(
    @SerialName("response_type")
    val responseType: LlmResponseType,
    @SerialName("spoken_text")
    val spokenText: String,
    @SerialName("summary_text")
    val summaryText: String? = null,
    @SerialName("safety_notes")
    val safetyNotes: List<String> = emptyList(),
    @SerialName("resume_policy")
    val resumePolicy: ResumePolicy = ResumePolicy.RESUME_SAME_STEP,
)

@Serializable
enum class LlmResponseType {
    @SerialName("personalized_step")
    PERSONALIZED_STEP,

    @SerialName("question_answer")
    QUESTION_ANSWER,

    @SerialName("fallback")
    FALLBACK,
}

@Serializable
enum class ResumePolicy {
    @SerialName("resume_same_step")
    RESUME_SAME_STEP,

    @SerialName("resume_next_step")
    RESUME_NEXT_STEP,

    @SerialName("retriage")
    RETRIAGE,
}
