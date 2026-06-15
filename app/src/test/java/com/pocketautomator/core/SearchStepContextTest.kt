package com.pocketautomator.core

import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStepContextTest {

    @Test
    fun isSearchInputStep_matchesSpotifyEditTextWithoutResourceId() {
        assertTrue(
            SearchStepContext.isSearchInputStep(
                ExportStep(
                    timestamp = 0,
                    screen = ScreenNode(nodeId = "1"),
                    action = ExportAction(
                        type = "set_text",
                        className = "android.widget.EditText",
                        path = listOf(0, 0, 1),
                        value = "edm playlist"
                    ),
                    packageName = "com.spotify.music"
                )
            )
        )
    }

    @Test
    fun findPrecedingSearchStep_skipsPlaceholderValues() {
        val steps = listOf(
            searchTextStep("edm playlist"),
            ExportStep(
                timestamp = 1,
                screen = ScreenNode(nodeId = "2"),
                action = ExportAction(
                    type = "set_text",
                    resourceId = "com.spotify.music:id/query",
                    value = "What do you want to listen to?"
                ),
                packageName = "com.spotify.music"
            ),
            clickStep()
        )

        val found = SearchStepContext.findPrecedingSearchStep(steps, beforeIndex = 2)

        assertEquals("edm playlist", found?.action?.value)
    }

    @Test
    fun findPrecedingSearchStep_skipsIntermediateSteps() {
        val steps = listOf(
            searchTextStep("edm playlist"),
            scrollStep(),
            clickStep()
        )

        val found = SearchStepContext.findPrecedingSearchStep(steps, beforeIndex = 2)

        assertEquals("edm playlist", found?.action?.value)
    }

    @Test
    fun findPrecedingSearchStep_returnsNullWhenNoSearchInput() {
        val steps = listOf(
            ExportStep(
                timestamp = 0,
                screen = ScreenNode(nodeId = "1"),
                action = ExportAction(type = "set_text", value = "hello"),
                packageName = "com.spotify.music"
            ),
            clickStep()
        )

        assertNull(SearchStepContext.findPrecedingSearchStep(steps, beforeIndex = 1))
    }

    private fun searchTextStep(value: String) = ExportStep(
        timestamp = 0,
        screen = ScreenNode(nodeId = "1"),
        action = ExportAction(
            type = "set_text",
            resourceId = "com.spotify.music:id/query",
            value = value
        ),
        packageName = "com.spotify.music"
    )

    private fun scrollStep() = ExportStep(
        timestamp = 1,
        screen = ScreenNode(nodeId = "2"),
        action = ExportAction(type = "scroll", direction = "forward"),
        packageName = "com.spotify.music"
    )

    private fun clickStep() = ExportStep(
        timestamp = 2,
        screen = ScreenNode(nodeId = "3"),
        action = ExportAction(
            type = "click",
            resourceId = "com.spotify.music:id/row_root",
            text = "Forms Of Love"
        ),
        packageName = "com.spotify.music"
    )
}
