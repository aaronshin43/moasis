package com.example.moasis

import com.example.moasis.ai.melange.classifyAiPreparationFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPreparationFailureTest {
    @Test
    fun bandwidth_limit_error_is_classified_as_retryable_quota_issue() {
        val failure = classifyAiPreparationFailure(
            throwable = IllegalStateException("HTTP Failed. Code is 429, Detail : Bandwidth limit exceeded"),
            hasInternetConnection = true,
        )

        assertTrue(failure.userMessage.contains("bandwidth quota", ignoreCase = true))
        assertTrue(failure.canRetry)
    }

    @Test
    fun x86_native_link_error_is_classified_as_non_retryable_abi_issue() {
        val failure = classifyAiPreparationFailure(
            throwable = UnsatisfiedLinkError("dlopen failed: library \"libggml-cpu.so\" not found"),
            hasInternetConnection = true,
        )

        assertTrue(failure.userMessage.contains("ABI", ignoreCase = true))
        assertFalse(failure.canRetry)
    }

    @Test
    fun local_play_asset_pack_failure_is_classified_as_retryable_delivery_issue() {
        val failure = classifyAiPreparationFailure(
            throwable = IllegalStateException("ItemStore: getItems RPC failed for item com.example.moasis"),
            hasInternetConnection = true,
        )

        assertTrue(failure.userMessage.contains("Play Asset Delivery", ignoreCase = true))
        assertTrue(failure.canRetry)
    }
}
