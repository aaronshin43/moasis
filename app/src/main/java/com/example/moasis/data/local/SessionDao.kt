package com.example.moasis.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query(
        """
        SELECT *
        FROM emergency_sessions
        WHERE status = :status
        ORDER BY updatedAtMs DESC
        """
    )
    fun observeSessionsByStatus(status: String = SessionStatus.ARCHIVED): Flow<List<EmergencySessionEntity>>

    @Query("SELECT * FROM emergency_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): EmergencySessionEntity?

    @Query(
        """
        SELECT *
        FROM session_messages
        WHERE sessionId = :sessionId
        ORDER BY messageIndex ASC
        """
    )
    suspend fun getMessages(sessionId: String): List<SessionMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: EmergencySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<SessionMessageEntity>)

    @Query("DELETE FROM session_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    @Query("DELETE FROM emergency_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Transaction
    suspend fun replaceSessionMessages(
        session: EmergencySessionEntity,
        messages: List<SessionMessageEntity>,
    ) {
        upsertSession(session)
        deleteMessages(session.sessionId)
        if (messages.isNotEmpty()) {
            upsertMessages(messages)
        }
    }
}

object SessionStatus {
    const val ARCHIVED = "archived"
}
