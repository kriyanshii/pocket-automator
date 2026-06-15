package com.pocketautomator.export

import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.JsonConfig
import com.pocketautomator.model.ScreenNode

/**
 * Converts recorded trajectories into supervised fine-tuning examples for future SLM training.
 *
 * Input:  Task + current UI tree
 * Output: click(text="EDM")
 */
object TrainingDataFormatter {

    fun toSftExample(recording: ExportRecording, step: ExportStep): SftExample {
        return SftExample(
            input = buildInput(recording.task, step.screen),
            output = formatAction(step.action.type, step.action)
        )
    }

    fun toSftExamples(recording: ExportRecording): List<SftExample> {
        return recording.steps.map { step -> toSftExample(recording, step) }
    }

    fun buildInput(task: String, screen: ScreenNode): String {
        return buildString {
            appendLine("Task:")
            appendLine(task)
            appendLine()
            appendLine("Current UI Tree:")
            append(JsonConfig.json.encodeToString(ScreenNode.serializer(), screen))
        }.trimEnd()
    }

    fun formatAction(type: String, action: com.pocketautomator.model.ExportAction): String {
        val args = buildList {
            action.resourceId?.let { add("resourceId=\"$it\"") }
            action.contentDescription?.let { add("contentDescription=\"$it\"") }
            action.text?.let { add("text=\"$it\"") }
            action.path?.let { add("path=$it") }
            if (type == "set_text" && action.value != null) {
                add("value=\"${action.value}\"")
            }
        }
        return if (args.isEmpty()) {
            "$type()"
        } else {
            "$type(${args.joinToString(", ")})"
        }
    }
}

data class SftExample(
    val input: String,
    val output: String
)
