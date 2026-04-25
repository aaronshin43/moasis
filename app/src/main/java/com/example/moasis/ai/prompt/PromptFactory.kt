package com.example.moasis.ai.prompt

import com.example.moasis.ai.model.LlmConstraints
import com.example.moasis.ai.model.LlmRequest
import com.example.moasis.ai.model.LlmRequestMode
import com.example.moasis.ai.model.LlmStyle
import com.example.moasis.domain.model.Protocol
import com.example.moasis.domain.model.ProtocolStep

class PromptFactory {
    fun buildPersonalizeStepRequest(
        scenarioId: String,
        protocol: Protocol,
        step: ProtocolStep,
        slots: Map<String, String>,
        targetListener: String = "caregiver",
    ): LlmRequest {
        return LlmRequest(
            mode = LlmRequestMode.PERSONALIZE_STEP,
            scenarioId = scenarioId,
            protocolId = protocol.protocolId,
            stepId = step.stepId,
            canonicalText = step.canonicalText,
            slots = slots,
            constraints = LlmConstraints(
                doNotAddNewSteps = true,
                doNotRemoveRequiredDetails = true,
                keepKeywords = step.mustKeepKeywords,
                forbiddenContent = step.forbiddenKeywords,
            ),
            style = LlmStyle(
                tone = "calm",
                length = "short",
                targetListener = targetListener,
            ),
        )
    }

    fun buildAnswerQuestionRequest(
        scenarioId: String,
        protocol: Protocol,
        step: ProtocolStep,
        userQuestion: String,
    ): LlmRequest {
        return LlmRequest(
            mode = LlmRequestMode.ANSWER_QUESTION,
            scenarioId = scenarioId,
            protocolId = protocol.protocolId,
            currentStepId = step.stepId,
            canonicalText = step.canonicalText,
            userQuestion = userQuestion,
            constraints = LlmConstraints(
                answerOnlyWithinCurrentContext = true,
                doNotChangeProtocolOrder = true,
                doNotMakeNewDiagnosis = true,
                keepKeywords = step.mustKeepKeywords,
                forbiddenContent = step.forbiddenKeywords,
            ),
            knownProhibitions = step.forbiddenKeywords,
        )
    }
}
