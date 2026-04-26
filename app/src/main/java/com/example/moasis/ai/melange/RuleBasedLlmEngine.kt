package com.example.moasis.ai.melange

import com.example.moasis.ai.model.LlmRequest
import com.example.moasis.ai.model.LlmRequestMode
import com.example.moasis.ai.model.LlmResponse
import com.example.moasis.ai.model.LlmResponseType
import com.example.moasis.ai.model.OnDeviceLlmEngine
import com.example.moasis.ai.model.ResumePolicy

class RuleBasedLlmEngine(
    private val shouldTimeout: Boolean = false,
) : OnDeviceLlmEngine {
    override fun generate(request: LlmRequest): Result<LlmResponse> {
        if (shouldTimeout) {
            return Result.failure(IllegalStateException("On-device LLM timed out."))
        }

        return Result.success(
            when (request.mode) {
                LlmRequestMode.PERSONALIZE_STEP -> LlmResponse(
                    responseType = LlmResponseType.PERSONALIZED_STEP,
                    spokenText = personalizeStep(request),
                    summaryText = request.canonicalText,
                    safetyNotes = request.constraints.forbiddenContent.map { "Avoid: $it" },
                    resumePolicy = ResumePolicy.RESUME_SAME_STEP,
                )

                LlmRequestMode.PERSONALIZE_QUESTION -> LlmResponse(
                    responseType = LlmResponseType.PERSONALIZED_STEP,
                    spokenText = request.canonicalText,
                    summaryText = request.canonicalText,
                    safetyNotes = emptyList(),
                    resumePolicy = ResumePolicy.RESUME_SAME_STEP,
                )

                LlmRequestMode.ANSWER_QUESTION -> LlmResponse(
                    responseType = LlmResponseType.QUESTION_ANSWER,
                    spokenText = answerQuestion(request),
                    summaryText = request.canonicalText,
                    safetyNotes = request.constraints.forbiddenContent.map { "Avoid: $it" },
                    resumePolicy = ResumePolicy.RESUME_SAME_STEP,
                )
            }
        )
    }

    private fun personalizeStep(request: LlmRequest): String {
        val listener = request.style?.targetListener
        return when (listener) {
            "caregiver" -> "Help them now: ${request.canonicalText}"
            "child" -> "Stay calm. ${request.canonicalText}"
            else -> request.canonicalText
        }
    }

    private fun answerQuestion(request: LlmRequest): String {
        val question = request.userQuestion.orEmpty().lowercase()
        val canonical = request.canonicalText
        return when {
            "ice" in question -> "Do not use ice directly. $canonical"
            else -> "Stay with the current step. $canonical"
        }
    }
}
