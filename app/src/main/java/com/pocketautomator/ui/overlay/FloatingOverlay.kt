package com.pocketautomator.ui.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketautomator.model.RecordingMeta

@Composable
fun FloatingOverlay(controller: OverlayController) {
    val isRecording by controller.isRecording
    val stepCount by controller.stepCount
    val currentApp by controller.currentApp
    val currentTask by controller.currentTask
    val statusMessage by controller.statusMessage
    val showTaskDialog by controller.showTaskDialog
    val showRecordingPicker by controller.showRecordingPicker
    val showExportPicker by controller.showExportPicker
    val recordings by controller.recordings

    val panelOpen = showTaskDialog || showRecordingPicker || showExportPicker

    Card(
        modifier = Modifier
            .padding(4.dp)
            .widthIn(min = 220.dp, max = 280.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pocket Automator",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            controller.moveOverlay(dragAmount.x.toInt(), dragAmount.y.toInt())
                        }
                    }
            )
            Spacer(modifier = Modifier.height(4.dp))

            when {
                showTaskDialog -> {
                    OverlayTaskNameForm(
                        onDismiss = { controller.dismissDialogs() },
                        onConfirm = { task -> controller.startRecording(task) }
                    )
                }
                showRecordingPicker -> {
                    OverlayRecordingPicker(
                        title = "Replay Recording",
                        recordings = recordings,
                        onDismiss = { controller.dismissDialogs() },
                        onSelect = { id -> controller.replay(id) }
                    )
                }
                showExportPicker -> {
                    OverlayRecordingPicker(
                        title = "Export Recording",
                        recordings = recordings,
                        onDismiss = { controller.dismissDialogs() },
                        onSelect = { id -> controller.export(id) }
                    )
                }
                isRecording -> {
                    Text(
                        text = currentTask,
                        color = Color(0xFFFF4444),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    if (currentApp.isNotBlank()) {
                        Text(
                            text = currentApp,
                            color = Color(0xFFB0B0B0),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "$stepCount steps · $statusMessage",
                        color = Color(0xFFFF4444),
                        fontSize = 10.sp
                    )
                }
                else -> {
                    Text(
                        text = statusMessage,
                        color = Color(0xFFB0B0B0),
                        fontSize = 10.sp,
                        maxLines = 2
                    )
                }
            }

            if (!panelOpen) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRecording) {
                        FilledIconButton(
                            onClick = { controller.openTaskDialog() },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = "Record", tint = Color.White)
                        }
                    } else {
                        FilledIconButton(
                            onClick = { controller.stopRecording() },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFFF4444)
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                        }
                    }

                    FilledIconButton(
                        onClick = { controller.openRecordingPicker() },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF2196F3)),
                        enabled = !isRecording
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Replay", tint = Color.White)
                    }

                    FilledIconButton(
                        onClick = { controller.openExportPicker() },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF9C27B0)),
                        enabled = !isRecording
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Export", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTaskNameForm(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var taskName by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF4CAF50),
        unfocusedBorderColor = Color(0xFF666666),
        focusedLabelColor = Color(0xFF4CAF50),
        unfocusedLabelColor = Color(0xFFB0B0B0),
        cursorColor = Color.White
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Task name",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = taskName,
            onValueChange = { taskName = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. play my edm playlist", color = Color(0xFF888888), fontSize = 11.sp) },
            singleLine = true,
            colors = fieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (taskName.isNotBlank()) onConfirm(taskName.trim())
                }
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFB0B0B0), fontSize = 11.sp)
            }
            TextButton(
                onClick = { if (taskName.isNotBlank()) onConfirm(taskName.trim()) },
                enabled = taskName.isNotBlank()
            ) {
                Text("Start", color = Color(0xFF4CAF50), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun OverlayRecordingPicker(
    title: String,
    recordings: List<RecordingMeta>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (recordings.isEmpty()) {
            Text("No recordings yet.", color = Color(0xFFB0B0B0), fontSize = 10.sp)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                items(recordings, key = { it.id }) { meta ->
                    TextButton(
                        onClick = { onSelect(meta.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = meta.task,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "${meta.app.ifBlank { "unknown" }} · ${meta.stepCount} steps",
                                color = Color(0xFFB0B0B0),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFB0B0B0), fontSize = 11.sp)
            }
        }
    }
}
