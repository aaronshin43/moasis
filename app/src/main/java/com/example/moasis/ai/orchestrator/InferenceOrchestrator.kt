package com.example.moasis.ai.orchestrator

import android.util.Log
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
        return validatePersonalizationOrFallback(step, response)
    }

    fun answerQuestion(
        scenarioId: String,
        protocol: Protocol,
        step: ProtocolStep,
        userQuestion: String,
        slots: Map<String, String> = emptyMap(),
    ): OrchestratedResponse {
        val request = promptFactory.buildAnswerQuestionRequest(
            scenarioId = scenarioId,
            protocol = protocol,
            step = step,
            userQuestion = userQuestion,
            slots = slots,
        )
        val response = llmEngine.generate(request)
        return validateQuestionAnswerOrFallback(step, response)
    }

    fun personalizeQuestion(
        scenarioId: String,
        treeId: String,
        nodeId: String,
        questionText: String,
        slots: Map<String, String>,
        targetListener: String = "patient",
    ): OrchestratedResponse {
        val request = promptFactory.buildPersonalizeQuestionRequest(
            scenarioId = scenarioId,
            treeId = treeId,
            nodeId = nodeId,
            questionText = questionText,
            slots = slots,
            targetListener = targetListener,
        )
        val response = llmEngine.generate(request)
        return validateQuestionPersonalizationOrFallback(questionText, response)
    }

    private fun validatePersonalizationOrFallback(
        step: ProtocolStep,
        response: Result<LlmResponse>,
    ): OrchestratedResponse {
        val llmResponse = response.getOrNull()
        if (llmResponse == null) {
            safeLogWarn("LLM call failed. Falling back to canonical step.")
            return OrchestratedResponse(
                spokenText = step.canonicalText,
                usedFallback = true,
                fallbackReason = response.exceptionOrNull()?.message ?: "LLM call failed.",
            )
        }

        val validation = responseValidator.validatePersonalizedStep(
            canonicalText = step.canonicalText,
            responseText = llmResponse.spokenText,
            mustKeepKeywords = step.mustKeepKeywords,
            forbiddenKeywords = step.forbiddenKeywords,
        )
        if (!validation.isValid) {
            safeLogWarn(
                "Validation fallback reason=\"${validation.reason}\" responsePreview=\"${llmResponse.spokenText.preview()}\" canonicalStep=${step.stepId}",
            )
        }
        return OrchestratedResponse(
            spokenText = validation.resolvedText,
            usedFallback = !validation.isValid,
            fallbackReason = validation.reason,
        )
    }

    private fun validateQuestionAnswerOrFallback(
        step: ProtocolStep,
        response: Result<LlmResponse>,
    ): OrchestratedResponse {
        val llmResponse = response.getOrNull()
        if (llmResponse == null) {
            safeLogWarn("LLM question-answer call failed. Falling back to safe short answer.")
            return OrchestratedResponse(
                spokenText = "I can't confirm that safely. Follow the current step and avoid extra remedies.",
                usedFallback = true,
                fallbackReason = response.exceptionOrNull()?.message ?: "LLM call failed.",
            )
        }

        val validation = responseValidator.validateQuestionAnswer(
            canonicalText = step.canonicalText,
            responseText = llmResponse.spokenText,
            mustKeepKeywords = step.mustKeepKeywords,
            forbiddenKeywords = step.forbiddenKeywords,
        )
        if (!validation.isValid) {
            safeLogWarn(
                "Question-answer fallback reason=\"${validation.reason}\" responsePreview=\"${llmResponse.spokenText.preview()}\" canonicalStep=${step.stepId}",
            )
        }
        return OrchestratedResponse(
            spokenText = validation.resolvedText,
            usedFallback = !validation.isValid,
            fallbackReason = validation.reason,
        )
    }

    private fun validateQuestionPersonalizationOrFallback(
        canonicalQuestion: String,
        response: Result<LlmResponse>,
    ): OrchestratedResponse {
        val llmResponse = response.getOrNull()
        if (llmResponse == null) {
            safeLogWarn("LLM question personalization failed. Falling back to canonical question.")
            return OrchestratedResponse(
                spokenText = canonicalQuestion,
                usedFallback = true,
                fallbackReason = response.exceptionOrNull()?.message ?: "LLM call failed.",
            )
        }

        val validation = responseValidator.validatePersonalizedQuestion(
            canonicalText = canonicalQuestion,
            responseText = llmResponse.spokenText,
        )
        if (!validation.isValid) {
            safeLogWarn(
                "Question personalization fallback reason=\"${validation.reason}\" responsePreview=\"${llmResponse.spokenText.preview()}\"",
            )
        }
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

private fun String.preview(maxLength: Int = 120): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { text ->
            if (text.length <= maxLength) text else text.take(maxLength) + "..."
        }
}

private fun safeLogWarn(message: String) {
    runCatching {
        Log.w(TAG, message)
    }
}

private const val TAG = "InferenceOrchestrator"
