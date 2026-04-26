package com.example.moasis.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_sessions")
data class EmergencySessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val category: String?,
    val protocolId: String?,
    val status: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val completedAtMs: Long?,
    val lastStepIndex: Int?,
    val totalSteps: Int?,
)
