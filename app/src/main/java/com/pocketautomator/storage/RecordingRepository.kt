package com.pocketautomator.storage

import com.pocketautomator.core.StepPackageResolver
import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.JsonConfig
import com.pocketautomator.model.RecordingMeta
import com.pocketautomator.model.RecordingStep
import com.pocketautomator.model.ScreenNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecordingRepository(private val dao: RecordingDao) {

    fun observeSessions(): Flow<List<RecordingMeta>> =
        dao.observeSessions().map { sessions -> sessions.map { it.toMeta() } }

    suspend fun listSessions(): List<RecordingMeta> =
        dao.listSessions().map { it.toMeta() }

    suspend fun createSession(id: String, taskName: String, createdAt: Long) {
        dao.insertSession(
            RecordingSessionEntity(
                id = id,
                taskName = taskName,
                packageName = "",
                createdAt = createdAt
            )
        )
    }

    suspend fun addStep(sessionId: String, step: RecordingStep) {
        if (step.packageName.isNotBlank()) {
            dao.updatePackageName(sessionId, step.packageName)
        }
        dao.insertStep(
            RecordingStepEntity(
                sessionId = sessionId,
                timestamp = step.timestamp,
                packageName = step.packageName,
                screenJson = JsonConfig.json.encodeToString(ScreenNode.serializer(), step.screen),
                actionJson = JsonConfig.json.encodeToString(ExportAction.serializer(), step.action)
            )
        )
    }

    suspend fun updateLastStep(sessionId: String, step: RecordingStep) {
        val stepId = dao.getLastStepId(sessionId) ?: return
        if (step.packageName.isNotBlank()) {
            dao.updatePackageName(sessionId, step.packageName)
        }
        dao.updateStep(
            id = stepId,
            timestamp = step.timestamp,
            packageName = step.packageName,
            screenJson = JsonConfig.json.encodeToString(ScreenNode.serializer(), step.screen),
            actionJson = JsonConfig.json.encodeToString(ExportAction.serializer(), step.action)
        )
    }

    suspend fun loadRecording(sessionId: String): ExportRecording? {
        val session = dao.getSession(sessionId) ?: return null
        val steps = dao.getSteps(sessionId).map { entity ->
            val screen = JsonConfig.json.decodeFromString(ScreenNode.serializer(), entity.screenJson)
            val action = JsonConfig.json.decodeFromString(ExportAction.serializer(), entity.actionJson)
            val packageName = entity.packageName.ifBlank {
                StepPackageResolver.resolve(
                    ExportStep(timestamp = entity.timestamp, screen = screen, action = action)
                ).orEmpty()
            }
            ExportStep(
                timestamp = entity.timestamp,
                screen = screen,
                action = action,
                packageName = packageName
            )
        }
        return ExportRecording(
            id = session.id,
            task = session.taskName,
            app = session.packageName,
            createdAt = session.createdAt,
            steps = steps
        )
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    suspend fun stepCount(sessionId: String): Int = dao.stepCount(sessionId)

    private fun SessionWithStepCount.toMeta() = RecordingMeta(
        id = id,
        task = taskName,
        app = packageName,
        stepCount = stepCount,
        createdAt = createdAt
    )
}
