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
            Log.d(TAG, "Prompt chars=${prompt.length} preview=\"${prompt.preview()}\"")

            runCatching { model.cleanUp() }
            Log.d(TAG, "Starting LLM generation mode=${request.mode} protocol=${request.protocolId} step=${request.stepId ?: request.currentStepId ?: "unknown"}")

            runCatching {
                model.run(prompt)
            }.getOrElse {
                Log.e(TAG, "model.run failed", it)
                return Result.failure(it)
            }

            val generationStartedAtMs = System.currentTimeMillis()
            val maxGenerationMillis = request.maxGenerationMillis()
            val maxGeneratedTokens = request.maxGeneratedTokens()
            val output = StringBuilder()
            var generatedTokenCount = 0
            while (true) {
                val elapsedMs = System.currentTimeMillis() - generationStartedAtMs
                if (elapsedMs > maxGenerationMillis) {
                    Log.w(
                        TAG,
                        "LLM generation timed out after ${elapsedMs}ms mode=${request.mode} generatedTokens=$generatedTokenCount chars=${output.length}",
                    )
                    runCatching { model.cleanUp() }
                    break
                }

                val waitResult = runCatching { model.waitForNextToken() }
                    .getOrElse {
                        Log.e(TAG, "waitForNextToken failed", it)
                        runCatching { model.cleanUp() }
                        return Result.failure(it)
                    }
                if (waitResult.generatedTokens == 0) {
                    break
                }
                generatedTokenCount = waitResult.generatedTokens
                if (waitResult.token.isNotEmpty()) {
                    output.append(waitResult.token)
                }
                if (waitResult.generatedTokens >= maxGeneratedTokens) {
                    Log.w(
                        TAG,
                        "LLM generation reached token cap=$maxGeneratedTokens mode=${request.mode} chars=${output.length}",
                    )
                    runCatching { model.cleanUp() }
                    break
                }
            }

            runCatching { model.cleanUp() }

            val rawOutput = output.toString()
            val spokenText = sanitizeOutput(rawOutput)
                .ifBlank { request.canonicalText }
            Log.d(
                TAG,
                "Completed LLM generation generatedTokens=$generatedTokenCount rawChars=${rawOutput.length} sanitizedChars=${spokenText.length} preview=\"${spokenText.preview()}\"",
            )
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
        return if (modelManager.configuredModelName().contains("qwen", ignoreCase = true)) {
            buildQwenPrompt(request)
        } else {
            buildGenericPrompt(request)
        }
    }

    private fun buildGenericPrompt(request: LlmRequest): String {
        val keepKeywords = request.constraints.keepKeywords
            .joinToString(", ")
            .ifBlank { "none" }
        val forbiddenContent = request.constraints.forbiddenContent
            .joinToString(", ")
            .ifBlank { "none" }
        val slotSummary = request.slots.entries
            .joinToString(separator = "\n") { (key, value) -> "- $key: $value" }
            .ifBlank { "- none" }

        val systemPrompt = buildString {
            appendLine("You are assisting with first-aid guidance.")
            appendLine("Stay within the provided protocol step.")
            appendLine("Do not add new actions, diagnoses, or reordered steps.")
            appendLine("Keep this response short and directly actionable.")
            appendLine("Do not output thinking, reasoning traces, or <think> tags.")
            appendLine("Required keywords must appear verbatim, exactly as written.")
            appendLine("Do not paraphrase or substitute the required keywords.")
        }.trim()

        val userPrompt = buildString {
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
                    appendLine("Respond in one sentence, ideally under 24 words.")
                    appendLine("Prefer the canonical wording. Only simplify tone and phrasing around the required keywords.")
                    appendLine("Return only the spoken instruction.")
                }

                LlmRequestMode.ANSWER_QUESTION -> {
                    appendLine("Task: Answer the user question briefly, then remain aligned to the current step.")
                    appendLine("User question: ${request.userQuestion.orEmpty()}")
                    appendLine("Known prohibitions: ${request.knownProhibitions.joinToString(", ").ifBlank { "none" }}")
                    appendLine("Respond in one or two short sentences, ideally under 40 words.")
                    appendLine("If required keywords apply to this step, include them verbatim.")
                    appendLine("Return only the answer text.")
                }
            }
        }.trim()

        return buildString {
            append("<bos>")
            append("<|turn>system\n")
            append(systemPrompt)
            append('\n')
            append("<|turn>user\n")
            append(userPrompt)
            append('\n')
            append("<|turn>model\n")
        }
    }

    private fun buildQwenPrompt(request: LlmRequest): String {
        val keepKeywords = request.constraints.keepKeywords
            .joinToString(", ")
            .ifBlank { "none" }
        val forbiddenContent = request.constraints.forbiddenContent
            .joinToString(", ")
            .ifBlank { "none" }
        val slotSummary = request.slots.entries
            .joinToString(separator = "\n") { (key, value) -> "- $key: $value" }
            .ifBlank { "- none" }

        val systemPrompt = buildString {
            appendLine("You rewrite or answer first-aid guidance.")
            appendLine("Stay within the provided protocol step.")
            appendLine("Do not add, reorder, or remove medical actions.")
            appendLine("Required keywords must appear verbatim.")
            appendLine("Do not output extra reasoning.")
        }.trim()

        val userPrompt = buildString {
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
                    appendLine("Task: Rewrite the canonical instruction in one short sentence.")
                    appendLine("Target listener: ${request.style?.targetListener ?: "caregiver"}")
                    appendLine("Keep the same meaning and keep the required keywords unchanged.")
                }

                LlmRequestMode.ANSWER_QUESTION -> {
                    appendLine("Task: Answer the question briefly in one or two short sentences.")
                    appendLine("User question: ${request.userQuestion.orEmpty()}")
                    appendLine("Answer the user's question directly first.")
                    appendLine("Do not simply repeat or paraphrase the canonical instruction.")
                    appendLine("Keep the answer aligned to the current step.")
                    appendLine("Known prohibitions: ${request.knownProhibitions.joinToString(", ").ifBlank { "none" }}")
                }
            }
        }.trim()

        return buildString {
            append("<|im_start|>system\n")
            append(systemPrompt)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userPrompt)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
            append("<think>\n\n</think>\n\n")
        }
    }

    private fun sanitizeOutput(rawText: String): String {
        return rawText
            .replace(Regex("<\\|channel\\>thought\\n.*?<channel\\|>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<think>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("</?think>", RegexOption.IGNORE_CASE), " ")
            .replace("<|turn>model", " ")
            .replace("<|turn|>", " ")
            .replace("<eos>", " ")
            .replace("<bos>", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun LlmRequest.maxGeneratedTokens(): Int {
        return when (mode) {
            LlmRequestMode.PERSONALIZE_STEP -> 96
            LlmRequestMode.ANSWER_QUESTION -> 144
        }
    }

    private fun LlmRequest.maxGenerationMillis(): Long {
        return when (mode) {
            LlmRequestMode.PERSONALIZE_STEP -> 20_000L
            LlmRequestMode.ANSWER_QUESTION -> 30_000L
        }
    }

    private fun String.preview(maxLength: Int = 120): String {
        return replace(Regex("\\s+"), " ")
            .trim()
            .let { text ->
                if (text.length <= maxLength) text else text.take(maxLength) + "..."
            }
    }

    companion object {
        private const val TAG = "MelangeLlmEngine"
    }
}
