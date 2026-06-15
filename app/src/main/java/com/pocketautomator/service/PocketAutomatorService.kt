package com.pocketautomator.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pocketautomator.PocketAutomatorApp
import com.pocketautomator.R
import com.pocketautomator.core.AppLabelResolver
import com.pocketautomator.core.AppLauncher
import com.pocketautomator.core.NodeFinder
import com.pocketautomator.core.RecorderEngine
import com.pocketautomator.core.ReplayEngine
import com.pocketautomator.core.StepPackageResolver
import com.pocketautomator.core.TreeCapture
import com.pocketautomator.core.WindowRootFinder
import com.pocketautomator.export.RecordingExporter
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.ui.MainActivity
import com.pocketautomator.ui.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PocketAutomatorService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var treeCapture: TreeCapture
    private lateinit var recorderEngine: RecorderEngine
    private lateinit var replayEngine: ReplayEngine
    private lateinit var overlayController: OverlayController
    private lateinit var recordingExporter: RecordingExporter

    override fun onCreate() {
        super.onCreate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val repository = PocketAutomatorApp.instance.recordingRepository
        treeCapture = TreeCapture()
        recorderEngine = RecorderEngine(treeCapture, repository) { rootInActiveWindow }
        replayEngine = ReplayEngine(
            nodeFinder = NodeFinder(),
            rootsProvider = { packageName -> findAllRootsForPackage(packageName) },
            navigator = { packageName ->
                if (StepPackageResolver.isLauncherPackage(packageName)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } else {
                    AppLauncher.launch(this, packageName)
                }
            },
            labelResolver = { label -> AppLabelResolver.packageForLabel(packageManager, label) },
            keyboardDismisser = { dismissSoftKeyboard() }
        )
        recordingExporter = RecordingExporter(this)
        overlayController = OverlayController(
            service = this,
            recorderEngine = recorderEngine,
            replayEngine = replayEngine,
            recordingRepository = repository,
            recordingExporter = recordingExporter,
            onStartRecording = { startForegroundNotification() },
            onStopRecording = { stopForegroundNotification() }
        )

        recorderEngine.onStepRecorded = { count, app ->
            overlayController.updateStepCount(count, app)
        }

        replayEngine.onStepResult = { result ->
            overlayController.updateReplayStatus(result.message)
        }

        replayEngine.onComplete = { success, message ->
            overlayController.onReplayComplete(success, message)
        }

        instance = this
        overlayController.show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (::replayEngine.isInitialized && replayEngine.isReplaying) return
        if (::recorderEngine.isInitialized && recorderEngine.isRecording()) {
            recorderEngine.handleEvent(event)
        }
    }

    override fun onInterrupt() {
        if (::overlayController.isInitialized && ::recorderEngine.isInitialized && recorderEngine.isRecording()) {
            overlayController.stopRecording()
        }
        if (::overlayController.isInitialized) {
            overlayController.hide()
        }
    }

    override fun onDestroy() {
        if (::overlayController.isInitialized) {
            overlayController.hide()
        }
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun replayRecording(recording: ExportRecording) {
        if (!::replayEngine.isInitialized) return
        serviceScope.launch {
            replayEngine.replay(recording)
        }
    }

    fun showOverlay() {
        if (::overlayController.isInitialized) {
            overlayController.show()
        }
    }

    fun startRecording(task: String) {
        if (::overlayController.isInitialized) {
            overlayController.startRecording(task)
        }
    }

    fun stopRecording(): Boolean {
        if (!::overlayController.isInitialized || !::recorderEngine.isInitialized) {
            return false
        }
        if (!recorderEngine.isRecording()) {
            return false
        }
        overlayController.stopRecording()
        return true
    }

    fun isRecording(): Boolean =
        ::recorderEngine.isInitialized && recorderEngine.isRecording()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RECORDING) {
            stopRecording()
        }
        return START_NOT_STICKY
    }

    val recorder: RecorderEngine?
        get() = if (::recorderEngine.isInitialized) recorderEngine else null

    private fun findAllRootsForPackage(packageName: String?): List<AccessibilityNodeInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return WindowRootFinder.findAllRootsForPackage(packageName, emptyList()) { rootInActiveWindow }
        }
        val windows = windows
        return try {
            WindowRootFinder.findAllRootsForPackage(packageName, windows) { rootInActiveWindow }
        } finally {
            windows.forEach { it.recycle() }
        }
    }

    private fun findRootForPackage(packageName: String?): AccessibilityNodeInfo? {
        return findAllRootsForPackage(packageName).firstOrNull()
    }

    private fun dismissSoftKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            softKeyboardController.showMode = SHOW_MODE_HIDDEN
        }
    }

    private fun startForegroundNotification() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PocketAutomatorService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(
                recorderEngine.currentTask()?.let { "Task: $it" }
                    ?: getString(R.string.notification_recording_text)
            )
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_stop_action),
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        @Volatile
        var instance: PocketAutomatorService? = null
            private set

        private const val CHANNEL_ID = "pocket_automator_recording"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP_RECORDING = "com.pocketautomator.action.STOP_RECORDING"

        fun isRunning(): Boolean =
            instance?.let { it::recorderEngine.isInitialized } == true
    }
}
