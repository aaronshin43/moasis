package com.example.moasis.data.local

import com.example.moasis.domain.model.EmergencySessionDetail
import com.example.moasis.domain.model.EmergencySessionMessage
import com.example.moasis.domain.model.EmergencySessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(
    private val sessionDao: SessionDao,
) {
    fun observeEarlierSessions(): Flow<List<EmergencySessionSummary>> {
        return sessionDao.observeSessionsByStatus()
            .map { sessions -> sessions.map { it.toSummary() } }
    }

    suspend fun archiveSession(
        sessionId: String,
        title: String,
        category: String?,
        protocolId: String?,
        createdAtMs: Long,
        updatedAtMs: Long,
        lastStepIndex: Int?,
        totalSteps: Int?,
        messages: List<SessionArchiveMessage>,
    ) {
        val session = EmergencySessionEntity(
            sessionId = sessionId,
            title = title.ifBlank { "Emergency session" },
            category = category,
            protocolId = protocolId,
            status = SessionStatus.ARCHIVED,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            completedAtMs = updatedAtMs,
            lastStepIndex = lastStepIndex,
            totalSteps = totalSteps,
        )
        val messageEntities = messages.mapIndexed { index, message ->
            SessionMessageEntity(
                sessionId = sessionId,
                messageIndex = index,
                role = message.role,
                title = message.title,
                text = message.text,
                secondaryText = message.secondaryText,
                warningText = message.warningText,
                visualAidIds = message.visualAidIds.joinToString(","),
                createdAtMs = updatedAtMs,
            )
        }
        sessionDao.replaceSessionMessages(session, messageEntities)
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
    }

    suspend fun getSessionDetail(sessionId: String): EmergencySessionDetail? {
        val session = sessionDao.getSession(sessionId) ?: return null
        val messages = sessionDao.getMessages(sessionId).map { message ->
            EmergencySessionMessage(
                role = message.role,
                title = message.title,
                text = message.text,
                secondaryText = message.secondaryText,
                warningText = message.warningText,
                visualAidIds = message.visualAidIds
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            )
        }
        return EmergencySessionDetail(
            summary = session.toSummary(),
            messages = messages,
        )
    }

    private fun EmergencySessionEntity.toSummary(): EmergencySessionSummary {
        val elapsedMs = (updatedAtMs - createdAtMs).coerceAtLeast(0L)
        val durationMinutes = elapsedMs / MILLIS_PER_MINUTE
        return EmergencySessionSummary(
            sessionId = sessionId,
            title = title,
            category = category,
            protocolId = protocolId,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            durationMinutes = durationMinutes,
        )
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

data class SessionArchiveMessage(
    val role: String,
    val title: String?,
    val text: String,
    val secondaryText: String?,
    val warningText: String?,
    val visualAidIds: List<String>,
)
