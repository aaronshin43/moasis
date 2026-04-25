package com.example.moasis.presentation

import com.example.moasis.domain.model.VisualAid
import kotlinx.serialization.Serializable

@Serializable
data class UiState(
    val title: String = "",
    val primaryInstruction: String = "",
    val secondaryInstruction: String? = null,
    val guidanceOriginLabel: String? = null,
    val warningText: String? = null,
    val checklist: List<ChecklistItem> = emptyList(),
    val visualAids: List<VisualAid> = emptyList(),
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val showCallEmergencyButton: Boolean = false,
)

@Serializable
data class ChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean = false,
)
