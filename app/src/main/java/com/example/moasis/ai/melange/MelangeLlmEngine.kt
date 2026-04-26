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
                        LlmRequestMode.PERSONALIZE_QUESTION -> LlmResponseType.PERSONALIZED_STEP
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
        return buildQwenPrompt(request)
    }

    private fun buildQwenPrompt(request: LlmRequest): String {
        val keepKeywords = request.constraints.keepKeywords
            .joinToString(", ")
            .ifBlank { "none" }
        val forbiddenContent = request.constraints.forbiddenContent
            .joinToString(", ")
            .ifBlank { "none" }
        val listener = request.style?.targetListener ?: "caregiver"
        val bodyLocation = request.slots["location"]?.takeIf { it.isNotBlank() }

        val systemPrompt = buildString {
            appendLine("You help with first-aid wording.")
            appendLine("Stay inside the current step.")
            appendLine("Do not add, remove, reorder, or diagnose.")
            appendLine("Do not output reasoning.")
        }.trim()

        val userPrompt = buildString {
            when (request.mode) {
                LlmRequestMode.PERSONALIZE_STEP -> {
                    appendLine("Task: rewrite the current step as one short spoken sentence.")
                    appendLine("Listener: $listener")
                    appendLine("Current step: ${request.canonicalText}")
                    bodyLocation?.let {
                        appendLine("User mentioned body part: $it")
                    }
                    appendLine("Must keep exact words: $keepKeywords")
                    appendLine("Do not say: $forbiddenContent")
                    appendLine("Rules:")
                    appendLine("- Keep the action and order unchanged.")
                    appendLine("- Keep the required words exactly as written.")
                    if (bodyLocation != null) {
                        appendLine("- If the step uses a body-part example, you may replace it with the user's mentioned body part.")
                        appendLine("- Only substitute the body-part reference. Do not change the care action.")
                    }
                    appendLine("- Return one sentence only.")
                }

                LlmRequestMode.PERSONALIZE_QUESTION -> {
                    appendLine("Task: rewrite this triage question in one or two short spoken sentences.")
                    appendLine("Listener: $listener")
                    appendLine("Current question: ${request.canonicalText}")
                    bodyLocation?.let {
                        appendLine("User mentioned body part: $it")
                    }
                    appendLine("Known details: ${request.slots.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifBlank { "none" }}")
                    appendLine("Rules:")
                    appendLine("- If the original manual text is a question, keep it as a question.")
                    appendLine("- Keep every risk condition and answer choice.")
                    appendLine("- Make the wording more natural and specific to the user's context.")
                    appendLine("- If the user already mentioned a body part, you may replace a generic reference like \"the burn\" with that body part.")
                    appendLine("- Do not remove any listed red flags or body-part checks.")
                    appendLine("- Return one or two short sentences only.")
                }

                LlmRequestMode.ANSWER_QUESTION -> {
                    appendLine("Task: answer the user's question directly in one or two short sentences.")
                    appendLine("Question: ${request.userQuestion.orEmpty()}")
                    appendLine("Current step: ${request.canonicalText}")
                    appendLine("Known details: ${request.slots.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifBlank { "none" }}")
                    appendLine("Unsafe or forbidden content: $forbiddenContent")
                    appendLine("Known prohibitions: ${request.knownProhibitions.joinToString(", ").ifBlank { "none" }}")
                    appendLine("Rules:")
                    appendLine("- Answer the question first.")
                    appendLine("- If the asked action is unsafe, start with \"No.\"")
                    appendLine("- If the asked action is acceptable from this step, start with \"Yes.\"")
                    appendLine("- Do not repeat the full current step.")
                    appendLine("- If unsure, say you cannot confirm it safely from this step.")
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
            .replace("<start_of_turn>model", " ")
            .replace("<start_of_turn>user", " ")
            .replace("<end_of_turn>", " ")
            .replace("<eos>", " ")
            .replace("<bos>", " ")
            .replace("<|im_end|>", " ")
            .replace("<|im_start|>assistant", " ")
            .replace("<|im_start|>user", " ")
            .replace("<|im_start|>system", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun LlmRequest.maxGeneratedTokens(): Int {
        return when (mode) {
            LlmRequestMode.PERSONALIZE_STEP -> 64
            LlmRequestMode.PERSONALIZE_QUESTION -> 72
            LlmRequestMode.ANSWER_QUESTION -> 96
        }
    }

    private fun LlmRequest.maxGenerationMillis(): Long {
        return when (mode) {
            LlmRequestMode.PERSONALIZE_STEP -> 20_000L
            LlmRequestMode.PERSONALIZE_QUESTION -> 20_000L
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
