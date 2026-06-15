package com.pocketautomator.core

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketautomator.model.Action
import com.pocketautomator.model.ActionType
import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.RecordingStep
import com.pocketautomator.storage.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

sealed class RecorderState {
    data object Idle : RecorderState()
    data class Recording(
        val task: String,
        val sessionId: String,
        val currentApp: String = ""
    ) : RecorderState()
}

class RecorderEngine(
    private val treeCapture: TreeCapture,
    private val repository: RecordingRepository,
    private val rootProvider: () -> AccessibilityNodeInfo?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var state: RecorderState = RecorderState.Idle
        private set

    private val steps = mutableListOf<RecordingStep>()
    private var recordingStartTime = 0L
    private var lastRecordedAction: Action? = null
    private var lastRecordedTime = 0L
    var onStepRecorded: ((Int, String) -> Unit)? = null

    fun startRecording(task: String): String {
        val id = UUID.randomUUID().toString()
        steps.clear()
        lastRecordedAction = null
        lastRecordedTime = 0L
        recordingStartTime = System.currentTimeMillis()
        state = RecorderState.Recording(task, id)
        scope.launch {
            repository.createSession(id, task, recordingStartTime)
        }
        return id
    }

    fun stopRecording(): String? {
        val current = state as? RecorderState.Recording ?: return null
        state = RecorderState.Idle
        val count = steps.size
        val sessionId = current.sessionId
        steps.clear()
        lastRecordedAction = null
        lastRecordedTime = 0L
        if (count == 0) {
            scope.launch { repository.deleteSession(sessionId) }
            return null
        }
        return sessionId
    }

    fun handleEvent(event: AccessibilityEvent) {
        val recording = state as? RecorderState.Recording ?: return

        val packageName = event.packageName?.toString() ?: recording.currentApp
        if (packageName.isNotBlank() && packageName != recording.currentApp) {
            state = recording.copy(currentApp = packageName)
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> recordAction(event, packageName)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> Unit
            else -> Unit
        }
    }

    private fun recordAction(event: AccessibilityEvent, packageName: String) {
        val recording = state as? RecorderState.Recording ?: return
        val action = ActionExtractor.extract(event) ?: return
        if (action.type == ActionType.SET_TEXT && PlaceholderText.isPlaceholderValue(action.value)) {
            Log.d(TAG, "Skipped placeholder set_text value=${action.value}")
            return
        }
        val now = System.currentTimeMillis()
        if (shouldSkipAction(action, now)) {
            Log.d(TAG, "Skipped ${action.type}: ${actionSummary(action)}")
            return
        }

        val root = rootProvider() ?: return
        val screen = treeCapture.capture(root)
        root.recycle()

        if (action.type == ActionType.SET_TEXT) {
            val prevStep = steps.lastOrNull()
            if (
                prevStep != null &&
                prevStep.action.type == "set_text" &&
                prevStep.action.resourceId != null &&
                prevStep.action.resourceId == action.selector?.resourceId
            ) {
                val updated = prevStep.copy(
                    timestamp = now - recordingStartTime,
                    packageName = packageName,
                    screen = screen,
                    action = ExportAction.from(action)
                )
                if (PlaceholderText.isPlaceholderValue(updated.action.value)) {
                    Log.d(TAG, "Skipped coalesce to placeholder value=${updated.action.value}")
                    return
                }
                steps[steps.size - 1] = updated
                lastRecordedAction = action
                lastRecordedTime = now
                Log.d(
                    TAG,
                    "Coalesced set_text value=${action.value} codePoints=${TextNormalizer.codePoints(action.value.orEmpty())}"
                )
                onStepRecorded?.invoke(steps.size, packageName)
                scope.launch { repository.updateLastStep(recording.sessionId, updated) }
                return
            }
            Log.d(
                TAG,
                "Recorded set_text value=${action.value} codePoints=${TextNormalizer.codePoints(action.value.orEmpty())}"
            )
        }

        val step = RecordingStep(
            timestamp = now - recordingStartTime,
            packageName = packageName,
            screen = screen,
            action = ExportAction.from(action)
        )
        steps.add(step)
        lastRecordedAction = action
        lastRecordedTime = now
        onStepRecorded?.invoke(steps.size, packageName)

        scope.launch {
            repository.addStep(recording.sessionId, step)
        }
    }

    private fun shouldSkipAction(action: Action, now: Long): Boolean {
        val prev = lastRecordedAction ?: return false
        val elapsed = now - lastRecordedTime

        return when (action.type) {
            ActionType.SCROLL -> {
                if (prev.type == ActionType.SET_TEXT && elapsed < POST_TEXT_SCROLL_SUPPRESS_MS) {
                    true
                } else if (
                    prev.type == ActionType.SCROLL &&
                    prev.selector == action.selector &&
                    prev.value == action.value &&
                    elapsed < SCROLL_DEDUP_MS
                ) {
                    true
                } else {
                    false
                }
            }
            ActionType.CLICK, ActionType.LONG_CLICK -> {
                prev.type == action.type &&
                    prev.selector == action.selector &&
                    elapsed < CLICK_DEDUP_MS
            }
            ActionType.SET_TEXT -> false
        }
    }

    private fun actionSummary(action: Action): String {
        val selector = action.selector
        return buildList {
            add(action.type.name.lowercase())
            selector?.resourceId?.let { add("id=$it") }
            selector?.text?.let { add("text=$it") }
            action.value?.let { add("value=$it") }
        }.joinToString(" ")
    }

    fun isRecording(): Boolean = state is RecorderState.Recording

    fun currentTask(): String? = (state as? RecorderState.Recording)?.task

    fun currentApp(): String? = (state as? RecorderState.Recording)?.currentApp?.takeIf { it.isNotBlank() }

    fun stepCount(): Int = steps.size

    companion object {
        private const val TAG = "RecorderEngine"
        private const val SCROLL_DEDUP_MS = 800L
        private const val POST_TEXT_SCROLL_SUPPRESS_MS = 2_000L
        private const val CLICK_DEDUP_MS = 400L
    }
}
