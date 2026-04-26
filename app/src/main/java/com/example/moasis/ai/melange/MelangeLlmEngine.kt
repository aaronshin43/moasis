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
        val modelName = modelManager.configuredModelName()
        return when {
            modelName.contains("qwen", ignoreCase = true) -> buildQwenPrompt(request)
            modelName.contains("medgemma", ignoreCase = true) -> buildMedGemmaPrompt(request)
            modelName.contains("gemma", ignoreCase = true) -> buildGemmaPrompt(request)
            else -> buildGenericPrompt(request)
        }
    }

    private fun buildGenericPrompt(request: LlmRequest): String {
        val systemPrompt = buildString {
            appendLine("You are a first-aid assistant.")
            appendLine("Stay within the current protocol step.")
            appendLine("Do not add diagnosis or extra steps.")
            appendLine("Reply briefly with direct spoken guidance.")
        }.trim()

        val userPrompt = buildString {
            when (request.mode) {
                LlmRequestMode.PERSONALIZE_STEP -> {
                    appendLine("Rewrite this step as one short spoken instruction.")
                    appendLine("Step: ${request.canonicalText}")
                    appendLine("Keep exactly: ${request.constraints.keepKeywords.joinToString(", ").ifBlank { "none" }}")
                    appendLine("Listener: ${request.style?.targetListener ?: "caregiver"}")
                }

                LlmRequestMode.ANSWER_QUESTION -> {
                    appendLine("Answer the question briefly and stay aligned with this step.")
                    appendLine("Question: ${request.userQuestion.orEmpty()}")
                    appendLine("Step: ${request.canonicalText}")
                    appendLine("Avoid: ${request.constraints.forbiddenContent.joinToString(", ").ifBlank { "none" }}")
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

    private fun buildGemmaPrompt(request: LlmRequest): String {
        val systemPrompt = buildString {
            appendLine("You are a first-aid assistant.")
            appendLine("Use only the current step.")
            appendLine("Do not add diagnosis, extra steps, or reasoning.")
            appendLine("Reply with short spoken guidance.")
        }.trim()

        val userPrompt = buildString {
            when (request.mode) {
                LlmRequestMode.PERSONALIZE_STEP -> {
                    appendLine("Rewrite this step for speech in one short sentence.")
                    appendLine("Keep these words exact: ${request.constraints.keepKeywords.joinToString(", ").ifBlank { "none" }}")
                    appendLine("Step: ${request.canonicalText}")
                    appendLine("Listener: ${request.style?.targetListener ?: "caregiver"}")
                    appendLine("Start with: Please")
                }

                LlmRequestMode.ANSWER_QUESTION -> {
                    appendLine("Answer the question in one or two short sentences.")
                    appendLine("Do not repeat the whole step unless needed.")
                    appendLine("If the action is unsafe, say not to do it and keep the answer aligned with the step.")
                    appendLine("Question: ${request.userQuestion.orEmpty()}")
                    appendLine("Current step: ${request.canonicalText}")
                    appendLine("Known unsafe content: ${request.constraints.forbiddenContent.joinToString(", ").ifBlank { "none" }}")
                    appendLine("Start with: Please")
                }
            }
        }.trim()

        return buildString {
            append("<bos>")
            append("<start_of_turn>user\n")
            append("System:\n")
            append(systemPrompt)
            append("\n\nUser:\n")
            append(userPrompt)
            append("\n<end_of_turn>\n")
            append("<start_of_turn>model\n")
            append("Please ")
        }
    }

    private fun buildMedGemmaPrompt(request: LlmRequest): String {
        val systemPrompt = "First-aid assistant. Use only the current step. No diagnosis. No extra steps. No reasoning."

        val userPrompt = when (request.mode) {
            LlmRequestMode.PERSONALIZE_STEP -> {
                buildString {
                    append("Rewrite for speech in one short sentence. ")
                    append("Keep exact: ")
                    append(request.constraints.keepKeywords.joinToString(", ").ifBlank { "none" })
                    append(". Step: ")
                    append(request.canonicalText)
                    append(". Listener: ")
                    append(request.style?.targetListener ?: "caregiver")
                    append(".")
                }
            }

            LlmRequestMode.ANSWER_QUESTION -> {
                buildString {
                    append("Answer briefly and stay aligned with the current step. ")
                    append("If unsafe, say not to do it. ")
                    append("Question: ")
                    append(request.userQuestion.orEmpty())
                    append(". Step: ")
                    append(request.canonicalText)
                    append(". Avoid: ")
                    append(request.constraints.forbiddenContent.joinToString(", ").ifBlank { "none" })
                    append(".")
                }
            }
        }

        return buildString {
            append("<bos>")
            append("<start_of_turn>user\n")
            append("System: ")
            append(systemPrompt)
            append("\nUser: ")
            append(userPrompt)
            append("\n<end_of_turn>\n")
            append("<start_of_turn>model\n")
            append("Please ")
        }
    }

    private fun buildQwenPrompt(request: LlmRequest): String {
        val keepKeywords = request.constraints.keepKeywords
            .joinToString(", ")
            .ifBlank { "none" }
        val forbiddenContent = request.constraints.forbiddenContent
            .joinToString(", ")
            .ifBlank { "none" }
        val listener = request.style?.targetListener ?: "caregiver"

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
                    appendLine("Must keep exact words: $keepKeywords")
                    appendLine("Do not say: $forbiddenContent")
                    appendLine("Rules:")
                    appendLine("- Keep the action and order unchanged.")
                    appendLine("- Keep the required words exactly as written.")
                    appendLine("- Return one sentence only.")
                }

                LlmRequestMode.ANSWER_QUESTION -> {
                    appendLine("Task: answer the user's question directly in one or two short sentences.")
                    appendLine("Question: ${request.userQuestion.orEmpty()}")
                    appendLine("Current step: ${request.canonicalText}")
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
            LlmRequestMode.ANSWER_QUESTION -> 96
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
