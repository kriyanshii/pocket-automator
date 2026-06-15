package com.pocketautomator.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.JsonConfig
import kotlinx.serialization.Serializable

@Serializable
private data class TrainingExport(
    val task: String,
    val app: String,
    val steps: List<com.pocketautomator.model.ExportStep>
)

class RecordingExporter(private val context: Context) {

    fun toJson(recording: ExportRecording): String {
        val export = TrainingExport(
            task = recording.task,
            app = recording.app,
            steps = recording.steps
        )
        return JsonConfig.json.encodeToString(TrainingExport.serializer(), export)
    }

    fun share(recording: ExportRecording): Intent {
        val cacheDir = java.io.File(context.cacheDir, "exports").also { it.mkdirs() }
        val fileName = "${slugify(recording.task)}_${recording.createdAt}.json"
        val file = java.io.File(cacheDir, fileName)
        file.writeText(toJson(recording))

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, recording.task)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun saveToDownloads(recording: ExportRecording): Result<String> {
        return runCatching {
            val fileName = "${slugify(recording.task)}_${recording.createdAt}.json"
            val json = toJson(recording)
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/PocketAutomator"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create download entry")
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    ?: error("Failed to write download file")
                "PocketAutomator/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val downloads = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "PocketAutomator"
                )
                downloads.mkdirs()
                val file = java.io.File(downloads, fileName)
                file.writeText(json)
                file.absolutePath
            }
        }
    }

    private fun slugify(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(50)
            .ifEmpty { "recording" }
    }
}
