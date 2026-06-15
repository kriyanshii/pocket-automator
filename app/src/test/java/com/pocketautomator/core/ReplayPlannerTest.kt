package com.pocketautomator.core

import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayPlannerTest {

    @Test
    fun plan_skipsLauncherPreambleAndLaunchesTargetApp() {
        val recording = ExportRecording(
            task = "play playlist",
            app = "com.spotify.music",
            steps = listOf(
                launcherStep("Spotify"),
                launcherScrollStep(),
                spotifyStep()
            )
        )

        val plan = ReplayPlanner.plan(recording)

        assertEquals(2, plan.skippedLauncherSteps)
        assertEquals("com.spotify.music", plan.initialLaunchPackage)
        assertEquals(1, plan.steps.size)
        assertEquals(2, plan.steps.first().originalIndex)
    }

    @Test
    fun plan_keepsAllStepsWhenRecordingStartsInTargetApp() {
        val recording = ExportRecording(
            task = "play playlist",
            app = "com.spotify.music",
            steps = listOf(spotifyStep(), spotifyStep())
        )

        val plan = ReplayPlanner.plan(recording)

        assertEquals(0, plan.skippedLauncherSteps)
        assertEquals("com.spotify.music", plan.initialLaunchPackage)
        assertEquals(2, plan.steps.size)
    }

    @Test
    fun filterSpuriousScrolls_dropsScrollAfterSetTextAndDuplicateScrolls() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(0, searchTextStep()),
            ReplayPlanner.PlannedStep(1, whatsAppScrollStep()),
            ReplayPlanner.PlannedStep(2, whatsAppScrollStep()),
            ReplayPlanner.PlannedStep(3, clickStep())
        )

        val filtered = ReplayPlanner.filterSpuriousScrolls(steps)

        assertEquals(2, filtered.size)
        assertEquals(0, filtered[0].originalIndex)
        assertEquals(3, filtered[1].originalIndex)
    }

    @Test
    fun coalesceSetTextSteps_keepsOnlyFinalValuePerPathOnlyField() {
        val path = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val steps = listOf(
            ReplayPlanner.PlannedStep(0, linkedInSearchTextStep("a", path)),
            ReplayPlanner.PlannedStep(1, linkedInSearchTextStep("ary", path)),
            ReplayPlanner.PlannedStep(2, linkedInSearchTextStep("arya sheth", path)),
            ReplayPlanner.PlannedStep(3, clickStep())
        )

        val coalesced = ReplayPlanner.coalesceSetTextSteps(steps)

        assertEquals(2, coalesced.size)
        assertEquals("arya sheth", coalesced[0].step.action.value)
        assertEquals(2, coalesced[0].originalIndex)
    }

    @Test
    fun coalesceSetTextSteps_keepsOnlyFinalValuePerField() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(0, searchTextStep("b")),
            ReplayPlanner.PlannedStep(1, searchTextStep("bir")),
            ReplayPlanner.PlannedStep(2, searchTextStep("biraj")),
            ReplayPlanner.PlannedStep(3, clickStep())
        )

        val coalesced = ReplayPlanner.coalesceSetTextSteps(steps)

        assertEquals(2, coalesced.size)
        assertEquals("biraj", coalesced[0].step.action.value)
        assertEquals(2, coalesced[0].originalIndex)
    }

    @Test
    fun sanitizeSetTextSelectors_dropsPartialTextAndPathWhenResourceIdPresent() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(
                0,
                ExportStep(
                    timestamp = 0,
                    screen = ScreenNode(nodeId = "1"),
                    action = ExportAction(
                        type = "set_text",
                        resourceId = "com.whatsapp:id/search_input",
                        text = "b",
                        path = listOf(0, 1, 2),
                        className = "android.widget.EditText",
                        value = "b"
                    )
                )
            )
        )

        val sanitized = ReplayPlanner.sanitizeSetTextSelectors(steps).first().step.action

        assertNull(sanitized.text)
        assertNull(sanitized.path)
        assertEquals("com.whatsapp:id/search_input", sanitized.resourceId)
    }

    @Test
    fun sanitizeSetTextSelectors_infersWhatsAppEntryForMessageInput() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(
                0,
                ExportStep(
                    timestamp = 0,
                    screen = ScreenNode(nodeId = "1"),
                    action = ExportAction(
                        type = "set_text",
                        className = "android.widget.EditText",
                        value = "Testing Ignore my messages,"
                    ),
                    packageName = "com.whatsapp"
                )
            )
        )

        val sanitized = ReplayPlanner.sanitizeSetTextSelectors(steps).first().step.action

        assertEquals("com.whatsapp:id/entry", sanitized.resourceId)
        assertNull(sanitized.text)
    }

    @Test
    fun filterPlaceholderSetTextSteps_dropsSpotifyHintText() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(
                0,
                ExportStep(
                    timestamp = 0,
                    screen = ScreenNode(nodeId = "1"),
                    action = ExportAction(
                        type = "set_text",
                        resourceId = "com.spotify.music:id/query",
                        value = "What do you want to listen to?"
                    ),
                    packageName = "com.spotify.music"
                )
            ),
            ReplayPlanner.PlannedStep(
                1,
                ExportStep(
                    timestamp = 1,
                    screen = ScreenNode(nodeId = "2"),
                    action = ExportAction(
                        type = "click",
                        text = "Forms Of Love"
                    ),
                    packageName = "com.spotify.music"
                )
            )
        )

        val filtered = ReplayPlanner.filterPlaceholderSetTextSteps(steps)

        assertEquals(1, filtered.size)
        assertEquals("click", filtered.first().step.action.type)
    }

    @Test
    fun rewritePostSearchClicks_rewritesClickAfterIntermediateScroll() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(0, spotifySearchTextStep("edm playlist")),
            ReplayPlanner.PlannedStep(
                1,
                ExportStep(
                    timestamp = 1,
                    screen = ScreenNode(nodeId = "2"),
                    action = ExportAction(type = "scroll", direction = "forward"),
                    packageName = "com.spotify.music"
                )
            ),
            ReplayPlanner.PlannedStep(
                2,
                ExportStep(
                    timestamp = 2,
                    screen = ScreenNode(nodeId = "3"),
                    action = ExportAction(
                        type = "click",
                        resourceId = "com.spotify.music:id/row_root",
                        text = "Forms Of Love",
                        path = listOf(0, 0, 4)
                    ),
                    packageName = "com.spotify.music"
                )
            )
        )

        val rewritten = ReplayPlanner.rewritePostSearchClicks(steps)[2].step.action

        assertNull(rewritten.resourceId)
        assertEquals("Forms Of Love", rewritten.text)
        assertNull(rewritten.path)
    }

    @Test
    fun rewritePostSearchClicks_replacesSpotifyRowClickWithResultTitle() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(
                0,
                ExportStep(
                    timestamp = 0,
                    screen = ScreenNode(nodeId = "1"),
                    action = ExportAction(
                        type = "set_text",
                        resourceId = "com.spotify.music:id/query",
                        value = "edm playlist"
                    ),
                    packageName = "com.spotify.music"
                )
            ),
            ReplayPlanner.PlannedStep(
                1,
                ExportStep(
                    timestamp = 1,
                    screen = ScreenNode(nodeId = "2"),
                    action = ExportAction(
                        type = "click",
                        resourceId = "com.spotify.music:id/row_root",
                        text = "EDM House Mix",
                        path = listOf(0, 0, 0, 3, 1, 0, 0, 2)
                    ),
                    packageName = "com.spotify.music"
                )
            )
        )

        val rewritten = ReplayPlanner.rewritePostSearchClicks(steps)[1].step.action

        assertNull(rewritten.resourceId)
        assertEquals("EDM House Mix", rewritten.text)
        assertNull(rewritten.path)
    }

    @Test
    fun rewritePostSearchClicks_replacesYouTubePlaylistClickWithContentDescription() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(
                0,
                ExportStep(
                    timestamp = 0,
                    screen = ScreenNode(nodeId = "1"),
                    action = ExportAction(
                        type = "set_text",
                        resourceId = "com.google.android.youtube:id/search_edit_text",
                        value = "ghibli food"
                    ),
                    packageName = "com.google.android.youtube"
                )
            ),
            ReplayPlanner.PlannedStep(
                1,
                ExportStep(
                    timestamp = 1,
                    screen = ScreenNode(nodeId = "2"),
                    action = ExportAction(
                        type = "click",
                        contentDescription = "Playlist - STUDIO GHIBLI FOOD COMPILATION - Savoring Anime: A Culinary Compilation - 9 videos",
                        path = listOf(0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 2, 0),
                        className = "android.widget.Button"
                    ),
                    packageName = "com.google.android.youtube"
                )
            )
        )

        val rewritten = ReplayPlanner.rewritePostSearchClicks(steps)[1].step.action

        assertNull(rewritten.resourceId)
        assertEquals(
            "Playlist - STUDIO GHIBLI FOOD COMPILATION - Savoring Anime: A Culinary Compilation - 9 videos",
            rewritten.text
        )
        assertNull(rewritten.path)
    }

    @Test
    fun rewritePostSearchClicks_replacesMisrecordedEntryClickWithContactRow() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(0, searchTextStep("biraj")),
            ReplayPlanner.PlannedStep(
                1,
                ExportStep(
                    timestamp = 1,
                    screen = ScreenNode(nodeId = "2"),
                    action = ExportAction(
                        type = "click",
                        resourceId = "com.whatsapp:id/entry",
                        text = "Message",
                        className = "android.widget.EditText"
                    ),
                    packageName = "com.whatsapp"
                )
            )
        )

        val rewritten = ReplayPlanner.rewritePostSearchClicks(steps)[1].step.action

        assertEquals("com.whatsapp:id/contact_row_container", rewritten.resourceId)
        assertEquals("biraj", rewritten.text)
        assertNull(rewritten.path)
    }

    @Test
    fun sanitizeClickSelectors_dropsPlaceholderTextOnEntry() {
        val steps = listOf(
            ReplayPlanner.PlannedStep(
                0,
                ExportStep(
                    timestamp = 0,
                    screen = ScreenNode(nodeId = "1"),
                    action = ExportAction(
                        type = "click",
                        resourceId = "com.whatsapp:id/entry",
                        text = "Message"
                    ),
                    packageName = "com.whatsapp"
                )
            )
        )

        val sanitized = ReplayPlanner.sanitizeClickSelectors(steps).first().step.action

        assertNull(sanitized.text)
        assertNull(sanitized.path)
    }

    private fun linkedInSearchTextStep(value: String, path: List<Int>) = ExportStep(
        timestamp = 0,
        screen = ScreenNode(nodeId = "1"),
        action = ExportAction(
            type = "set_text",
            className = "android.widget.EditText",
            path = path,
            value = value
        ),
        packageName = "com.linkedin.android"
    )

    private fun searchTextStep(value: String) = ExportStep(
        timestamp = 0,
        screen = ScreenNode(nodeId = "1"),
        action = ExportAction(
            type = "set_text",
            resourceId = "com.whatsapp:id/search_input",
            value = value
        ),
        packageName = "com.whatsapp"
    )

    private fun searchTextStep() = searchTextStep("Ri")

    private fun whatsAppScrollStep() = ExportStep(
        timestamp = 1,
        screen = ScreenNode(nodeId = "2"),
        action = ExportAction(
            type = "scroll",
            resourceId = "com.whatsapp:id/result_list",
            direction = "forward"
        ),
        packageName = "com.whatsapp"
    )

    private fun clickStep() = ExportStep(
        timestamp = 2,
        screen = ScreenNode(nodeId = "3"),
        action = ExportAction(
            type = "click",
            resourceId = "com.whatsapp:id/contact_row_container",
            className = "android.widget.RelativeLayout"
        ),
        packageName = "com.whatsapp"
    )

    private fun launcherStep(appLabel: String) = ExportStep(
        timestamp = 0,
        screen = ScreenNode(nodeId = "1"),
        action = ExportAction(
            type = "click",
            resourceId = "com.motorola.launcher3:id/icon",
            contentDescription = appLabel,
            text = appLabel
        ),
        packageName = "com.motorola.launcher3"
    )

    private fun launcherScrollStep() = ExportStep(
        timestamp = 1,
        screen = ScreenNode(nodeId = "2"),
        action = ExportAction(
            type = "scroll",
            resourceId = "com.motorola.launcher3:id/apps_list_view"
        ),
        packageName = "com.motorola.launcher3"
    )

    private fun spotifySearchTextStep(value: String) = ExportStep(
        timestamp = 0,
        screen = ScreenNode(nodeId = "1"),
        action = ExportAction(
            type = "set_text",
            resourceId = "com.spotify.music:id/query",
            value = value
        ),
        packageName = "com.spotify.music"
    )

    private fun spotifyStep() = ExportStep(
        timestamp = 2,
        screen = ScreenNode(nodeId = "3"),
        action = ExportAction(type = "click", text = "Search"),
        packageName = "com.spotify.music"
    )
}
