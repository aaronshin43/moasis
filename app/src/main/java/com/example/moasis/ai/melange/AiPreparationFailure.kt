package com.example.moasis.ai.melange

import java.io.IOException

data class AiPreparationFailure(
    val userMessage: String,
    val canRetry: Boolean,
    val detail: String? = null,
)

fun classifyAiPreparationFailure(
    throwable: Throwable,
    hasInternetConnection: Boolean,
): AiPreparationFailure {
    val messageChain = generateSequence(throwable) { it.cause }
        .joinToString(" ") { cause ->
            listOfNotNull(cause::class.simpleName, cause.message)
                .joinToString(": ")
        }
        .ifBlank { throwable.toString() }

    return when {
        "bandwidth limit exceeded" in messageChain.lowercase() || "code is 429" in messageChain.lowercase() -> {
            AiPreparationFailure(
                userMessage = "Melange bandwidth quota is exhausted. Retry later or use a different project key.",
                canRetry = true,
                detail = messageChain,
            )
        }

        "libggml-cpu.so" in messageChain || "unsatisfiedlinkerror" in messageChain.lowercase() -> {
            AiPreparationFailure(
                userMessage = "This device ABI cannot load the selected Melange runtime. Use an arm64 Android device.",
                canRetry = false,
                detail = messageChain,
            )
        }

        "itemstore" in messageChain.lowercase() || "assetpack" in messageChain.lowercase() || "onerror(-100)" in messageChain.lowercase() -> {
            AiPreparationFailure(
                userMessage = "Play Asset Delivery is unavailable for this local install. Use direct download or a Play-delivered build.",
                canRetry = true,
                detail = messageChain,
            )
        }

        !hasInternetConnection || throwable is IOException -> {
            AiPreparationFailure(
                userMessage = "No network is available for AI model download. Connect once and retry.",
                canRetry = true,
                detail = messageChain,
            )
        }

        else -> {
            AiPreparationFailure(
                userMessage = "AI model preparation failed. Retry and inspect the device log if it keeps failing.",
                canRetry = true,
                detail = messageChain,
            )
        }
    }
}
