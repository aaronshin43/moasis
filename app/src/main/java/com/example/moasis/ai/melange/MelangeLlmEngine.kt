package com.example.moasis.ai.melange

import android.util.Log
import com.example.moasis.ai.model.LlmRequest
import com.example.moasis.ai.model.LlmRequestMode
import com.example.moasis.ai.model.LlmResponse
import com.example.moasis.ai.model.LlmResponseType
import com.example.moasis.ai.model.OnDeviceLlmEngine
import com.example.moasis.ai.model.ResumePolicy

class MelangeLlmEngine(
    private val modelManager: MelangeModelManager,
) : OnDeviceLlmEngine {
    private val generationLock = Any()

    override fun generate(request: LlmRequest): Result<LlmResponse> {
        synchronized(generationLock) {
            val model = modelManager.getOrCreateModel()
                .getOrElse { return Result.failure(it) }
            val prompt = buildPrompt(request)

            runCatching { model.cleanUp() }
            Log.d(TAG, "Starting LLM generation mode=${request.mode} protocol=${request.protocolId} step=${request.stepId ?: request.currentStepId ?: "unknown"}")

            runCatching {
                model.run(prompt)
            }.getOrElse {
                Log.e(TAG, "model.run failed", it)
                return Result.failure(it)
            }

            val output = StringBuilder()
            while (true) {
                val waitResult = runCatching { model.waitForNextToken() }
                    .getOrElse {
                        Log.e(TAG, "waitForNextToken failed", it)
                        runCatching { model.cleanUp() }
                        return Result.failure(it)
                    }
                if (waitResult.generatedTokens == 0) {
                    break
                }
                if (waitResult.token.isNotEmpty()) {
                    output.append(waitResult.token)
                }
            }

            runCatching { model.cleanUp() }

            val spokenText = output.toString().trim().ifBlank { request.canonicalText }
            Log.d(TAG, "Completed LLM generation chars=${spokenText.length}")
            return Result.success(
                LlmResponse(
                    responseType = when (request.mode) {
                        LlmRequestMode.PERSONALIZE_STEP -> LlmResponseType.PERSONALIZED_STEP
                        LlmRequestMode.ANSWER_QUESTION -> LlmResponseType.QUESTION_ANSWER
                    },
                    spokenText = spokenText,
                    summaryText = request.canonicalText,
                    safetyNotes = request.constraints.forbiddenContent.map { "Avoid: $it" },
                    resumePolicy = ResumePolicy.RESUME_SAME_STEP,
                )
            )
        }
    }

    private fun buildPrompt(request: LlmRequest): String {
        val keepKeywords = request.constraints.keepKeywords
            .joinToString(", ")
            .ifBlank { "none" }
        val forbiddenContent = request.constraints.forbiddenContent
            .joinToString(", ")
            .ifBlank { "none" }
        val slotSummary = request.slots.entries
            .joinToString(separator = "\n") { (key, value) -> "- $key: $value" }
            .ifBlank { "- none" }

        return buildString {
            appendLine("You are assisting with first-aid guidance.")
            appendLine("Stay within the provided protocol step.")
            appendLine("Do not add new actions, diagnoses, or reordered steps.")
            appendLine("Keep this response short and directly actionable.")
            appendLine("Scenario: ${request.scenarioId}")
            appendLine("Protocol: ${request.protocolId}")
            appendLine("Current step: ${request.stepId ?: request.currentStepId ?: "unknown"}")
            appendLine("Canonical instruction: ${request.canonicalText}")
            appendLine("Required keywords: $keepKeywords")
            appendLine("Forbidden content: $forbiddenContent")
            appendLine("Slots:")
            appendLine(slotSummary)

            when (request.mode) {
                LlmRequestMode.PERSONALIZE_STEP -> {
                    appendLine("Task: Rewrite the canonical instruction for the target listener.")
                    appendLine("Target listener: ${request.style?.targetListener ?: "caregiver"}")
                    appendLine("Tone: ${request.style?.tone ?: "calm"}")
                    appendLine("Length: ${request.style?.length ?: "short"}")
                    appendLine("Return only the spoken instruction.")
                }

                LlmRequestMode.ANSWER_QUESTION -> {
                    appendLine("Task: Answer the user question briefly, then remain aligned to the current step.")
                    appendLine("User question: ${request.userQuestion.orEmpty()}")
                    appendLine("Known prohibitions: ${request.knownProhibitions.joinToString(", ").ifBlank { "none" }}")
                    appendLine("Return only the answer text.")
                }
            }
        }
    }

    companion object {
        private const val TAG = "MelangeLlmEngine"
    }
}
