package com.example.moasis.domain.model

data class EmergencySessionSummary(
    val sessionId: String,
    val title: String,
    val category: String?,
    val protocolId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val durationMinutes: Long,
)

data class EmergencySessionDetail(
    val summary: EmergencySessionSummary,
    val messages: List<EmergencySessionMessage>,
)

data class EmergencySessionMessage(
    val role: String,
    val title: String?,
    val text: String,
    val secondaryText: String?,
    val warningText: String?,
    val visualAidIds: List<String>,
)
