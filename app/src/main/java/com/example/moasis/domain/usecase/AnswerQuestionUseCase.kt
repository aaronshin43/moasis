package com.example.moasis.domain.usecase

import com.example.moasis.ai.orchestrator.InferenceOrchestrator
import com.example.moasis.data.protocol.ProtocolRepository

class AnswerQuestionUseCase(
    private val protocolRepository: ProtocolRepository,
    private val inferenceOrchestrator: InferenceOrchestrator,
) {
    fun answer(
        scenarioId: String,
        protocolId: String,
        stepIndex: Int,
        userQuestion: String,
        slots: Map<String, String> = emptyMap(),
    ): QuestionAnswerResult {
        val protocol = requireNotNull(protocolRepository.getProtocol(protocolId)) {
            "Missing protocol for $protocolId"
        }
        val step = requireNotNull(protocol.steps.getOrNull(stepIndex)) {
            "Missing step $stepIndex for $protocolId"
        }
        val aiResponse = inferenceOrchestrator.answerQuestion(
            scenarioId = scenarioId,
            protocol = protocol,
            step = step,
            userQuestion = userQuestion,
            slots = slots,
        )
        val resumeText = buildResumeText(step.canonicalText)
        return QuestionAnswerResult(
            answerText = aiResponse.spokenText,
            resumeText = resumeText,
            usedFallback = aiResponse.usedFallback,
            fallbackReason = aiResponse.fallbackReason,
        )
    }

    private fun buildResumeText(canonicalText: String): String {
        val shortened = canonicalText
            .substringBefore('.')
            .trim()
        return "Let's continue. $shortened."
    }
}

data class QuestionAnswerResult(
    val answerText: String,
    val resumeText: String,
    val usedFallback: Boolean,
    val fallbackReason: String? = null,
)
