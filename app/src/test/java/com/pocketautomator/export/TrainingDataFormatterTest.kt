package com.pocketautomator.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.ScreenNode

class TrainingDataFormatterTest {

    @Test
    fun formatAction_usesSemanticSelector() {
        val output = TrainingDataFormatter.formatAction(
            "click",
            ExportAction(type = "click", text = "EDM")
        )
        assertEquals("click(text=\"EDM\")", output)
    }

    @Test
    fun toSftExample_buildsInputOutputPair() {
        val recording = ExportRecording(
            task = "play my edm playlist",
            app = "com.spotify.music",
            steps = listOf(
                ExportStep(
                    timestamp = 100L,
                    screen = ScreenNode(nodeId = "1", text = "EDM"),
                    action = ExportAction(type = "click", text = "EDM")
                )
            )
        )
        val example = TrainingDataFormatter.toSftExample(recording, recording.steps.first())
        assertTrue(example.input.contains("Task:"))
        assertTrue(example.input.contains("play my edm playlist"))
        assertTrue(example.input.contains("Current UI Tree:"))
        assertEquals("click(text=\"EDM\")", example.output)
    }
}
