package com.pocketautomator.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSerializationTest {

    @Test
    fun exportRecording_roundTripsThroughJson() {
        val recording = ExportRecording(
            id = "test-id",
            task = "play my edm playlist",
            app = "com.spotify.music",
            createdAt = 1000L,
            steps = listOf(
                ExportStep(
                    timestamp = 100L,
                    screen = ScreenNode(
                        nodeId = "1",
                        text = "EDM",
                        resourceId = "com.spotify.music:id/playlist",
                        clickable = true,
                        bounds = "[12,34][200,100]",
                        children = emptyList()
                    ),
                    action = ExportAction(
                        type = "click",
                        text = "EDM",
                        path = listOf(0, 2)
                    )
                )
            )
        )

        val json = JsonConfig.json.encodeToString(ExportRecording.serializer(), recording)
        assertTrue(json.contains("play my edm playlist"))
        assertTrue(json.contains("EDM"))
        assertTrue(json.contains("\"type\": \"click\""))
        assertTrue(!json.contains("\"x\""))
        assertTrue(!json.contains("\"y\""))

        val decoded = JsonConfig.json.decodeFromString(ExportRecording.serializer(), json)
        assertEquals(recording.task, decoded.task)
        assertEquals(1, decoded.steps.size)
        assertEquals("click", decoded.steps[0].action.type)
        assertEquals("EDM", decoded.steps[0].action.text)
    }

    @Test
    fun exportAction_convertsToInternalAction() {
        val export = ExportAction(type = "long_click", resourceId = "com.app:id/item")
        val action = export.toAction()
        assertEquals(ActionType.LONG_CLICK, action.type)
        assertEquals("com.app:id/item", action.selector?.resourceId)
    }

    @Test
    fun exportAction_roundTripsScrollDirection() {
        val export = ExportAction(
            type = "scroll",
            resourceId = "com.app:id/list",
            direction = "backward"
        )
        val action = export.toAction()
        assertEquals(ActionType.SCROLL, action.type)
        assertEquals("backward", action.value)

        val roundTrip = ExportAction.from(action)
        assertEquals("backward", roundTrip.direction)
    }

    @Test
    fun exportAction_roundTripsClassName() {
        val export = ExportAction(
            type = "click",
            text = "Riti",
            className = "android.widget.TextView"
        )
        val action = export.toAction()
        assertEquals("android.widget.TextView", action.selector?.className)
    }
}
