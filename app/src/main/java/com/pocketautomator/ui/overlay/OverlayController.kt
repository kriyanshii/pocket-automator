package com.pocketautomator.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pocketautomator.core.RecorderEngine
import com.pocketautomator.core.ReplayEngine
import com.pocketautomator.export.RecordingExporter
import com.pocketautomator.model.RecordingMeta
import com.pocketautomator.service.PocketAutomatorService
import com.pocketautomator.storage.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OverlayController(
    private val service: PocketAutomatorService,
    private val recorderEngine: RecorderEngine,
    private val replayEngine: ReplayEngine,
    private val recordingRepository: RecordingRepository,
    private val recordingExporter: RecordingExporter,
    private val onStartRecording: () -> Unit,
    private val onStopRecording: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    val isRecording = mutableStateOf(false)
    val stepCount = mutableIntStateOf(0)
    val currentApp = mutableStateOf("")
    val currentTask = mutableStateOf("")
    val statusMessage = mutableStateOf("Ready")
    val showTaskDialog = mutableStateOf(false)
    val showRecordingPicker = mutableStateOf(false)
    val showExportPicker = mutableStateOf(false)
    val exportRecordingId = mutableStateOf<String?>(null)
    val recordings = mutableStateOf<List<RecordingMeta>>(emptyList())

    private var overlayFocusable = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 16
        y = 120
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        if (overlayFocusable == focusable) return
        overlayFocusable = focusable
        layoutParams.flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (composeView != null) return

        val lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FloatingOverlay(controller = this@OverlayController)
            }
        }
        windowManager.addView(composeView, layoutParams)
    }

    fun hide() {
        composeView?.let {
            windowManager.removeView(it)
            composeView = null
        }
    }

    fun startRecording(task: String) {
        recorderEngine.startRecording(task)
        isRecording.value = true
        stepCount.intValue = 0
        currentApp.value = ""
        currentTask.value = task
        statusMessage.value = "Recording"
        showTaskDialog.value = false
        setOverlayFocusable(false)
        onStartRecording()
    }

    fun stopRecording() {
        val savedCount = stepCount.intValue
        val sessionId = recorderEngine.stopRecording()
        isRecording.value = false
        currentApp.value = ""
        currentTask.value = ""
        onStopRecording()
        statusMessage.value = if (sessionId != null) {
            "Saved $savedCount steps"
        } else {
            "No steps recorded"
        }
        refreshRecordings()
    }

    fun moveOverlay(deltaX: Int, deltaY: Int) {
        layoutParams.x += deltaX
        layoutParams.y += deltaY
        composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    fun refreshRecordings() {
        scope.launch {
            recordings.value = recordingRepository.listSessions()
        }
    }

    fun replay(recordingId: String) {
        scope.launch {
            if (replayEngine.isReplaying) {
                statusMessage.value = "Replay already in progress"
                return@launch
            }
            if (isRecording.value) {
                stopRecording()
            }
            val recording = recordingRepository.loadRecording(recordingId) ?: run {
                statusMessage.value = "Recording not found"
                return@launch
            }
            dismissDialogs()
            setOverlayFocusable(false)
            statusMessage.value = "Replaying..."
            service.replayRecording(recording)
        }
    }

    fun export(recordingId: String) {
        scope.launch {
            val recording = recordingRepository.loadRecording(recordingId) ?: run {
                statusMessage.value = "Recording not found"
                return@launch
            }
            val result = recordingExporter.saveToDownloads(recording)
            statusMessage.value = result.fold(
                onSuccess = { "Exported to Downloads/$it" },
                onFailure = { "Export failed: ${it.message}" }
            )
            showExportPicker.value = false
            exportRecordingId.value = null
        }
    }

    fun updateStepCount(count: Int, app: String) {
        stepCount.intValue = count
        if (app.isNotBlank()) {
            currentApp.value = app
        }
        statusMessage.value = "Recording"
    }

    fun updateReplayStatus(message: String) {
        statusMessage.value = message
    }

    fun onReplayComplete(success: Boolean, message: String) {
        statusMessage.value = message
    }

    fun openTaskDialog() {
        showRecordingPicker.value = false
        showExportPicker.value = false
        showTaskDialog.value = true
        setOverlayFocusable(true)
    }

    fun openRecordingPicker() {
        refreshRecordings()
        showTaskDialog.value = false
        showExportPicker.value = false
        showRecordingPicker.value = true
        setOverlayFocusable(false)
    }

    fun openExportPicker() {
        refreshRecordings()
        showTaskDialog.value = false
        showRecordingPicker.value = false
        showExportPicker.value = true
        setOverlayFocusable(false)
    }

    fun dismissDialogs() {
        showTaskDialog.value = false
        showRecordingPicker.value = false
        showExportPicker.value = false
        exportRecordingId.value = null
        setOverlayFocusable(false)
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
