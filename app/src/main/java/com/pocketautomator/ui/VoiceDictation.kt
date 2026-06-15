package com.pocketautomator.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberVoiceDictation(
    onResult: (String) -> Unit,
    onError: (String) -> Unit
): VoiceDictationState {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode != Activity.RESULT_OK) {
            if (result.resultCode != Activity.RESULT_CANCELED) {
                onError("Voice recognition failed")
            }
            return@rememberLauncherForActivityResult
        }
        val matches = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = matches?.firstOrNull()?.trim().orEmpty()
        if (text.isBlank()) {
            onError("No speech detected")
        } else {
            onResult(text)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            onError("Microphone permission required")
            return@rememberLauncherForActivityResult
        }
        launchSpeechRecognizer(context, speechLauncher, onError) { isListening = it }
    }

    val startListening: () -> Unit = {
        if (!isListening) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                launchSpeechRecognizer(context, speechLauncher, onError) { isListening = it }
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    return remember(isListening) {
        VoiceDictationState(
            isListening = isListening,
            startListening = startListening
        )
    }
}

data class VoiceDictationState(
    val isListening: Boolean,
    val startListening: () -> Unit
)

private fun launchSpeechRecognizer(
    context: android.content.Context,
    speechLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onError: (String) -> Unit,
    onListeningChanged: (Boolean) -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your command")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    if (intent.resolveActivity(context.packageManager) == null) {
        onError("Speech recognition not available on this device")
        return
    }
    onListeningChanged(true)
    speechLauncher.launch(intent)
}
