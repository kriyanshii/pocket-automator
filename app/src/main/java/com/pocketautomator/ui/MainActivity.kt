package com.pocketautomator.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pocketautomator.PocketAutomatorApp
import com.pocketautomator.export.RecordingExporter
import com.pocketautomator.service.PocketAutomatorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var showRecordingScreen by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifications disabled — recording indicator may not show", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            PocketAutomatorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val service = PocketAutomatorService.instance
                    val recorder = service?.recorder
                    if (showRecordingScreen && recorder != null) {
                        RecordingScreen(
                            recorderEngine = recorder,
                            onBack = { showRecordingScreen = false },
                            onStop = {
                                if (service?.stopRecording() == true) {
                                    Toast.makeText(this@MainActivity, "Recording saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "No steps recorded", Toast.LENGTH_SHORT).show()
                                }
                                showRecordingScreen = false
                            }
                        )
                    } else {
                        RecordingsScreen(
                            recordingRepository = PocketAutomatorApp.instance.recordingRepository,
                            skillRepository = PocketAutomatorApp.instance.skillRepository,
                            trajectoryLoader = PocketAutomatorApp.instance.trajectoryLoader,
                            isAccessibilityEnabled = { isAccessibilityServiceEnabled() },
                            isServiceRunning = { PocketAutomatorService.isRunning() },
                            isRecording = { PocketAutomatorService.instance?.isRecording() == true },
                            onEnableAccessibility = { openAccessibilitySettings() },
                            onShowOverlay = { PocketAutomatorService.instance?.showOverlay() },
                            onStartRecording = { task ->
                                val service = PocketAutomatorService.instance
                                if (service == null) {
                                    Toast.makeText(this@MainActivity, "Enable accessibility service first", Toast.LENGTH_SHORT).show()
                                } else {
                                    service.startRecording(task)
                                    showRecordingScreen = true
                                    Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onStopRecording = {
                                val service = PocketAutomatorService.instance
                                if (service?.stopRecording() == true) {
                                    Toast.makeText(this@MainActivity, "Recording saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "No steps recorded", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenRecording = { showRecordingScreen = true },
                            onReplay = { id ->
                                scope.launch {
                                    val recording = PocketAutomatorApp.instance.recordingRepository
                                        .loadRecording(id)
                                    if (recording == null) {
                                        Toast.makeText(this@MainActivity, "Recording not found", Toast.LENGTH_SHORT).show()
                                    } else if (!PocketAutomatorService.isRunning()) {
                                        Toast.makeText(this@MainActivity, "Enable accessibility service first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        PocketAutomatorService.instance?.replayRecording(recording)
                                        Toast.makeText(this@MainActivity, "Replaying: ${recording.task}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onSkillReplay = { recording ->
                                PocketAutomatorService.instance?.replayRecording(recording)
                            },
                            onExport = { id ->
                                scope.launch {
                                    val recording = PocketAutomatorApp.instance.recordingRepository
                                        .loadRecording(id)
                                    if (recording == null) {
                                        Toast.makeText(this@MainActivity, "Recording not found", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    val result = RecordingExporter(this@MainActivity).saveToDownloads(recording)
                                    result.onSuccess { path ->
                                        Toast.makeText(this@MainActivity, "Exported to Downloads/$path", Toast.LENGTH_LONG).show()
                                    }.onFailure { e ->
                                        Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onDelete = { id ->
                                scope.launch {
                                    PocketAutomatorApp.instance.recordingRepository.deleteSession(id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        val expectedComponent = "$packageName/${PocketAutomatorService::class.java.canonicalName}"
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
