package com.example.moasis.presentation

data class EmergencyViewState(
    val screenMode: ScreenMode = ScreenMode.HOME,
    val uiState: UiState = UiState(),
    val statusText: String? = null,
    val quickResponses: List<String> = emptyList(),
    val isAiEnabled: Boolean = false,
    val aiStatusText: String? = null,
    val aiProgress: Float? = null,
    val isAiPreparing: Boolean = false,
    val isAiReady: Boolean = false,
    val canRetryAiPreparation: Boolean = false,
    val aiModelLabel: String? = null,
    val aiRouteText: String? = null,
    val aiCacheSummaryText: String? = null,
    val aiDiagnosticDetail: String? = null,
    val transcriptDraft: String = "",
    val speechRequestKey: Int = 0,
    val attachedImagePaths: List<String> = emptyList(),
)

enum class ScreenMode {
    HOME,
    ACTIVE,
}
