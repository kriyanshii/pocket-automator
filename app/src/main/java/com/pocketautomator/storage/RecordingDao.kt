package com.pocketautomator.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class SessionWithStepCount(
    val id: String,
    val taskName: String,
    val packageName: String,
    val createdAt: Long,
    val stepCount: Int
)

@Dao
interface RecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: RecordingStepEntity)

    @Query("SELECT * FROM recording_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): RecordingSessionEntity?

    @Query("SELECT * FROM recording_steps WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSteps(sessionId: String): List<RecordingStepEntity>

    @Query(
        """
        SELECT s.id, s.taskName, s.packageName, s.createdAt,
               COUNT(st.id) AS stepCount
        FROM recording_sessions s
        LEFT JOIN recording_steps st ON st.sessionId = s.id
        GROUP BY s.id
        ORDER BY s.createdAt DESC
        """
    )
    fun observeSessions(): Flow<List<SessionWithStepCount>>

    @Query(
        """
        SELECT s.id, s.taskName, s.packageName, s.createdAt,
               COUNT(st.id) AS stepCount
        FROM recording_sessions s
        LEFT JOIN recording_steps st ON st.sessionId = s.id
        GROUP BY s.id
        ORDER BY s.createdAt DESC
        """
    )
    suspend fun listSessions(): List<SessionWithStepCount>

    @Query("SELECT id FROM recording_steps WHERE sessionId = :sessionId ORDER BY id DESC LIMIT 1")
    suspend fun getLastStepId(sessionId: String): Long?

    @Query(
        """
        UPDATE recording_steps
        SET timestamp = :timestamp, packageName = :packageName,
            screenJson = :screenJson, actionJson = :actionJson
        WHERE id = :id
        """
    )
    suspend fun updateStep(
        id: Long,
        timestamp: Long,
        packageName: String,
        screenJson: String,
        actionJson: String
    )

    @Query("DELETE FROM recording_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE recording_sessions SET packageName = :packageName WHERE id = :sessionId")
    suspend fun updatePackageName(sessionId: String, packageName: String)

    @Query("SELECT COUNT(*) FROM recording_steps WHERE sessionId = :sessionId")
    suspend fun stepCount(sessionId: String): Int
}
