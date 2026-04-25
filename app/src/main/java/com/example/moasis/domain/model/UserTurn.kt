package com.example.moasis.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserTurn(
    val text: String? = null,
    val voiceTranscript: String? = null,
    val imageUris: List<String> = emptyList(),
    val timestamp: Long,
)
