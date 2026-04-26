package com.example.moasis.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "session_messages",
    primaryKeys = ["sessionId", "messageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = EmergencySessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class SessionMessageEntity(
    val sessionId: String,
    val messageIndex: Int,
    val role: String,
    val title: String?,
    val text: String,
    val secondaryText: String?,
    val warningText: String?,
    val visualAidIds: String,
    val createdAtMs: Long,
)
