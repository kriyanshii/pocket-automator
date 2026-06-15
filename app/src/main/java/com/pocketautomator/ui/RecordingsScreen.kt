package com.pocketautomator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketautomator.model.RecordingMeta
import com.pocketautomator.storage.RecordingRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    recordingRepository: RecordingRepository,
    isAccessibilityEnabled: () -> Boolean,
    isServiceRunning: () -> Boolean,
    isRecording: () -> Boolean,
    onEnableAccessibility: () -> Unit,
    onShowOverlay: () -> Unit,
    onStartRecording: (String) -> Unit,
    onStopRecording: () -> Unit,
    onOpenRecording: () -> Unit,
    onReplay: (String) -> Unit,
    onExport: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val recordings by recordingRepository.observeSessions().collectAsState(initial = emptyList())
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled()) }
    var serviceRunning by remember { mutableStateOf(isServiceRunning()) }
    var recordingActive by remember { mutableStateOf(isRecording()) }
    var showTaskDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accessibilityEnabled = isAccessibilityEnabled()
        serviceRunning = isServiceRunning()
        recordingActive = isRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Recordings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            StatusCard(
                accessibilityEnabled = accessibilityEnabled,
                serviceRunning = serviceRunning,
                recordingActive = recordingActive,
                onEnableAccessibility = onEnableAccessibility,
                onShowOverlay = onShowOverlay,
                onStartRecording = { showTaskDialog = true },
                onStopRecording = {
                    onStopRecording()
                    recordingActive = false
                },
                onOpenRecording = onOpenRecording
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (recordings.isEmpty()) {
                Text(
                    text = "No recordings yet. Tap Start Recording or use the floating overlay to capture interactions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recordings, key = { it.id }) { meta ->
                        RecordingCard(
                            meta = meta,
                            onReplay = { onReplay(meta.id) },
                            onExport = { onExport(meta.id) },
                            onDelete = { onDelete(meta.id) }
                        )
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        TaskNameDialog(
            onDismiss = { showTaskDialog = false },
            onConfirm = { task ->
                onStartRecording(task)
                recordingActive = true
                showTaskDialog = false
            }
        )
    }
}

@Composable
private fun StatusCard(
    accessibilityEnabled: Boolean,
    serviceRunning: Boolean,
    recordingActive: Boolean,
    onEnableAccessibility: () -> Unit,
    onShowOverlay: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenRecording: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Service Status", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (accessibilityEnabled) "Accessibility: Enabled" else "Accessibility: Disabled",
                color = if (accessibilityEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Text(
                text = if (serviceRunning) "Overlay: Active" else "Overlay: Inactive",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (recordingActive) {
                Text(
                    text = "Recording in progress",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!accessibilityEnabled) {
                    Button(onClick = onEnableAccessibility) {
                        Text("Enable Accessibility")
                    }
                }
                if (accessibilityEnabled && serviceRunning) {
                    if (recordingActive) {
                        Button(
                            onClick = onStopRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop Recording")
                        }
                        Button(onClick = onOpenRecording) {
                            Text("View Recording")
                        }
                    } else {
                        Button(onClick = onStartRecording) {
                            Text("Start Recording")
                        }
                    }
                    Button(onClick = onShowOverlay) {
                        Text("Show Overlay")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    meta: RecordingMeta,
    onReplay: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = meta.task, fontWeight = FontWeight.Medium)
            Text(
                text = meta.app.ifBlank { "unknown app" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${meta.stepCount} steps · ${dateFormat.format(Date(meta.createdAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onReplay) { Text("Replay") }
                TextButton(onClick = onExport) { Text("Export") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
