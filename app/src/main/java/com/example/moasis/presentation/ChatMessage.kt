package com.example.moasis.presentation

import com.example.moasis.domain.model.VisualAid

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Assistant(
        val title: String,
        val primaryInstruction: String,
        val secondaryInstruction: String?,
        val warningText: String?,
        val visualAids: List<VisualAid>,
        val currentStep: Int,
        val totalSteps: Int,
    ) : ChatMessage()
}
