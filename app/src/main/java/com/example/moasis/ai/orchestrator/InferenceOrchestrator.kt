package com.example.moasis.ai.orchestrator

import com.example.moasis.ai.model.LlmResponse
import com.example.moasis.ai.model.OnDeviceLlmEngine
import com.example.moasis.ai.prompt.PromptFactory
import com.example.moasis.domain.model.Protocol
import com.example.moasis.domain.model.ProtocolStep
import com.example.moasis.domain.safety.ResponseValidator

class InferenceOrchestrator(
    private val llmEngine: OnDeviceLlmEngine,
    private val promptFactory: PromptFactory,
    private val responseValidator: ResponseValidator,
) {
    fun personalizeStep(
        scenarioId: String,
        protocol: Protocol,
        step: ProtocolStep,
        slots: Map<String, String>,
        targetListener: String,
    ): OrchestratedResponse {
        val request = promptFactory.buildPersonalizeStepRequest(
            scenarioId = scenarioId,
            protocol = protocol,
            step = step,
            slots = slots,
            targetListener = targetListener,
        )
        val response = llmEngine.generate(request)
        return validateOrFallback(step, response)
    }

    fun answerQuestion(
        scenarioId: String,
        protocol: Protocol,
        step: ProtocolStep,
        userQuestion: String,
    ): OrchestratedResponse {
        val request = promptFactory.buildAnswerQuestionRequest(
            scenarioId = scenarioId,
            protocol = protocol,
            step = step,
            userQuestion = userQuestion,
        )
        val response = llmEngine.generate(request)
        return validateOrFallback(step, response)
    }

    private fun validateOrFallback(
        step: ProtocolStep,
        response: Result<LlmResponse>,
    ): OrchestratedResponse {
        val llmResponse = response.getOrNull()
        if (llmResponse == null) {
            return OrchestratedResponse(
                spokenText = step.canonicalText,
                usedFallback = true,
                fallbackReason = response.exceptionOrNull()?.message ?: "LLM call failed.",
            )
        }

        val validation = responseValidator.validate(
            canonicalText = step.canonicalText,
            responseText = llmResponse.spokenText,
            mustKeepKeywords = step.mustKeepKeywords,
            forbiddenKeywords = step.forbiddenKeywords,
        )
        return OrchestratedResponse(
            spokenText = validation.resolvedText,
            usedFallback = !validation.isValid,
            fallbackReason = validation.reason,
        )
    }
}

data class OrchestratedResponse(
    val spokenText: String,
    val usedFallback: Boolean,
    val fallbackReason: String? = null,
)
