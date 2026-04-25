package com.example.moasis.presentation

data class EmergencyViewState(
    val screenMode: ScreenMode = ScreenMode.HOME,
    val uiState: UiState = UiState(),
    val statusText: String? = null,
    val quickResponses: List<String> = emptyList(),
    val isAiEnabled: Boolean = false,
)

enum class ScreenMode {
    HOME,
    ACTIVE,
}
