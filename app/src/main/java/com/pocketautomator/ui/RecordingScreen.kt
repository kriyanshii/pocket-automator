package com.pocketautomator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketautomator.core.RecorderEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    recorderEngine: RecorderEngine,
    onBack: () -> Unit,
    onStop: () -> Unit
) {
    var taskName by mutableStateOf(recorderEngine.currentTask() ?: "")
    var currentApp by mutableStateOf(recorderEngine.currentApp() ?: "")
    var stepCount by mutableIntStateOf(recorderEngine.stepCount())
    var status by mutableStateOf(if (recorderEngine.isRecording()) "Recording" else "Idle")

    LaunchedEffect(recorderEngine) {
        while (recorderEngine.isRecording()) {
            taskName = recorderEngine.currentTask() ?: taskName
            currentApp = recorderEngine.currentApp() ?: currentApp
            stepCount = recorderEngine.stepCount()
            status = "Recording"
            delay(500)
        }
        status = "Stopped"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            RecordingInfoCard(
                label = "Task Name",
                value = taskName.ifBlank { "—" }
            )
            Spacer(modifier = Modifier.height(12.dp))
            RecordingInfoCard(
                label = "Current App",
                value = currentApp.ifBlank { "—" }
            )
            Spacer(modifier = Modifier.height(12.dp))
            RecordingInfoCard(
                label = "Step Count",
                value = stepCount.toString()
            )
            Spacer(modifier = Modifier.height(12.dp))
            RecordingInfoCard(
                label = "Recording Status",
                value = status,
                highlight = recorderEngine.isRecording()
            )

            if (recorderEngine.isRecording()) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop Recording")
                }
            }
        }
    }
}

@Composable
private fun RecordingInfoCard(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
