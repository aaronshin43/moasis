package com.example.moasis.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmbeddingPreparationState(
    val isEnabled: Boolean = false,
    val statusText: String? = null,
    val isPreparing: Boolean = false,
    val isReady: Boolean = false,
)

class EmbeddingPreparationStateHolder(
    initialState: EmbeddingPreparationState = EmbeddingPreparationState(),
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<EmbeddingPreparationState> = _state.asStateFlow()

    fun currentState(): EmbeddingPreparationState = _state.value

    fun markDisabled() {
        _state.value = EmbeddingPreparationState()
    }

    fun markPreparing(statusText: String?) {
        _state.value = EmbeddingPreparationState(
            isEnabled = true,
            statusText = statusText,
            isPreparing = true,
            isReady = false,
        )
    }

    fun markReady(statusText: String? = "Embedding model ready on device.") {
        _state.value = EmbeddingPreparationState(
            isEnabled = true,
            statusText = statusText,
            isPreparing = false,
            isReady = true,
        )
    }

    fun markFailed(statusText: String?) {
        _state.value = EmbeddingPreparationState(
            isEnabled = true,
            statusText = statusText,
            isPreparing = false,
            isReady = false,
        )
    }
}
