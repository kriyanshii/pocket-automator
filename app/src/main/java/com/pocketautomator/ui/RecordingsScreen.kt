package com.pocketautomator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketautomator.api.SkillRepository
import com.pocketautomator.core.LearnedSkill
import com.pocketautomator.core.SkillTrajectoryMapper
import com.pocketautomator.core.TrajectoryLoader
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.RecordingMeta
import com.pocketautomator.storage.RecordingRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface PredictUiState {
    data object Idle : PredictUiState
    data object Loading : PredictUiState
    data class Success(val skill: String) : PredictUiState
    data class Error(val message: String) : PredictUiState
}

@Composable
fun RecordingsScreen(
    recordingRepository: RecordingRepository,
    skillRepository: SkillRepository,
    trajectoryLoader: TrajectoryLoader,
    isAccessibilityEnabled: () -> Boolean,
    isServiceRunning: () -> Boolean,
    isRecording: () -> Boolean,
    onEnableAccessibility: () -> Unit,
    onShowOverlay: () -> Unit,
    onStartRecording: (String) -> Unit,
    onStopRecording: () -> Unit,
    onOpenRecording: () -> Unit,
    onReplay: (String) -> Unit,
    onSkillReplay: (ExportRecording) -> Unit,
    onExport: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val recordings by recordingRepository.observeSessions().collectAsState(initial = emptyList())
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled()) }
    var serviceRunning by remember { mutableStateOf(isServiceRunning()) }
    var recordingActive by remember { mutableStateOf(isRecording()) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var uiState by remember { mutableStateOf<PredictUiState>(PredictUiState.Idle) }
    val scope = rememberCoroutineScope()
    val learnedSkills = remember { SkillTrajectoryMapper.learnedSkills() }

    LaunchedEffect(Unit) {
        accessibilityEnabled = isAccessibilityEnabled()
        serviceRunning = isServiceRunning()
        recordingActive = isRecording()
    }

    val submitPrompt: (String) -> Unit = { text ->
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            uiState = PredictUiState.Error("Enter a prompt first")
        } else if (!isServiceRunning()) {
            uiState = PredictUiState.Error("Enable accessibility service first")
        } else {
            scope.launch {
                uiState = PredictUiState.Loading
                runCatching {
                    skillRepository.predictSkill(trimmed)
                }.onSuccess { skill ->
                    val recording = trajectoryLoader.loadRecording(skill)
                    if (recording == null) {
                        uiState = PredictUiState.Error("No trajectory for skill: $skill")
                    } else {
                        uiState = PredictUiState.Success(skill = skill)
                        onSkillReplay(recording)
                    }
                }.onFailure { e ->
                    uiState = PredictUiState.Error(e.message ?: "Prediction failed")
                }
            }
        }
    }

    val voiceDictation = rememberVoiceDictation(
        onResult = { text ->
            prompt = text
            submitPrompt(text)
        },
        onError = { message ->
            uiState = PredictUiState.Error(message)
        }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Pocket Automator",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Teach your phone new skills",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ServiceStatusCard(
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
            }

            item {
                PromptCard(
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    isLoading = uiState is PredictUiState.Loading,
                    isListening = voiceDictation.isListening,
                    onVoiceInput = voiceDictation.startListening,
                    onRun = { submitPrompt(prompt) }
                )
            }

            item {
                AiStatusCard(uiState = uiState)
            }

            item {
                LearnedSkillsSection(
                    skills = learnedSkills,
                    onSkillTap = { skill ->
                        prompt = skill.examplePrompt
                    }
                )
            }

            if (recordings.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saved Recordings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(recordings, key = { it.id }) { meta ->
                    RecordingCard(
                        meta = meta,
                        onReplay = { onReplay(meta.id) },
                        onExport = { onExport(meta.id) },
                        onDelete = { onDelete(meta.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
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
private fun ServiceStatusCard(
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator(
                    label = "Accessibility",
                    active = accessibilityEnabled
                )
                StatusIndicator(
                    label = "Service",
                    active = serviceRunning
                )
                if (recordingActive) {
                    StatusIndicator(
                        label = "Recording",
                        active = true,
                        activeColor = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!accessibilityEnabled || (accessibilityEnabled && serviceRunning)) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!accessibilityEnabled) {
                        Button(
                            onClick = onEnableAccessibility,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Enable Accessibility")
                        }
                    } else {
                        if (recordingActive) {
                            Button(
                                onClick = onStopRecording,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Stop")
                            }
                            Button(
                                onClick = onOpenRecording,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("View")
                            }
                        } else {
                            Button(
                                onClick = onStartRecording,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Record")
                            }
                        }
                        TextButton(onClick = onShowOverlay) {
                            Text("Overlay")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    label: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondary
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (active) activeColor
                    else MaterialTheme.colorScheme.outline
                )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PromptCard(
    prompt: String,
    onPromptChange: (String) -> Unit,
    isLoading: Boolean,
    isListening: Boolean,
    onVoiceInput: () -> Unit,
    onRun: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "What should your phone do?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                placeholder = {
                    Text(
                        "e.g. play my workout playlist",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && !isListening,
                singleLine = false,
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            if (isListening) {
                Text(
                    text = "Listening… speak your command",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onVoiceInput,
                    enabled = !isLoading && !isListening,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (isListening) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = if (isListening) "Listening" else "Voice input"
                    )
                }

                Button(
                    onClick = onRun,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isLoading && !isListening && prompt.isNotBlank(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running…")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiStatusCard(uiState: PredictUiState) {
    val (detectedSkill, executionStatus, statusColor) = when (uiState) {
        PredictUiState.Idle -> Triple("—", "Ready", MaterialTheme.colorScheme.onSurfaceVariant)
        PredictUiState.Loading -> Triple("Analyzing…", "Pending", MaterialTheme.colorScheme.primary)
        is PredictUiState.Success -> Triple(
            formatSkillName(uiState.skill),
            "Executing replay",
            MaterialTheme.colorScheme.secondary
        )
        is PredictUiState.Error -> Triple("—", uiState.message, MaterialTheme.colorScheme.error)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StatusRow(label = "Detected Skill", value = detectedSkill)
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))
            StatusRow(
                label = "Execution Status",
                value = executionStatus,
                valueColor = statusColor
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun LearnedSkillsSection(
    skills: List<LearnedSkill>,
    onSkillTap: (LearnedSkill) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Learned Skills",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        skills.forEach { skill ->
            LearnedSkillCard(
                skill = skill,
                onClick = { onSkillTap(skill) }
            )
        }
    }
}

@Composable
private fun LearnedSkillCard(
    skill: LearnedSkill,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = skillIcon(skill.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = skill.examplePrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun skillIcon(skillId: String): ImageVector = when (skillId) {
    "create_alarm" -> Icons.Filled.Alarm
    "spotify_play_playlist" -> Icons.Filled.MusicNote
    "whatsapp_send_message" -> Icons.AutoMirrored.Filled.Message
    else -> Icons.Filled.School
}

private fun formatSkillName(skill: String): String =
    skill.split('_').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }

@Composable
private fun RecordingCard(
    meta: RecordingMeta,
    onReplay: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onReplay) { Text("Replay") }
                TextButton(onClick = onExport) { Text("Export") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
