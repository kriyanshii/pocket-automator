package com.pocketautomator.core

import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepPackageResolverTest {

    @Test
    fun resolve_usesStoredPackageName() {
        val step = ExportStep(
            timestamp = 0,
            screen = ScreenNode(nodeId = "1"),
            action = ExportAction(type = "click"),
            packageName = "com.spotify.music"
        )
        assertEquals("com.spotify.music", StepPackageResolver.resolve(step))
    }

    @Test
    fun resolve_infersFromResourceId() {
        val step = ExportStep(
            timestamp = 0,
            screen = ScreenNode(nodeId = "1"),
            action = ExportAction(
                type = "scroll",
                resourceId = "com.motorola.launcher3:id/apps_list_view"
            )
        )
        assertEquals("com.motorola.launcher3", StepPackageResolver.resolve(step))
    }

    @Test
    fun isLauncherPackage_detectsMotorolaLauncher() {
        assertTrue(StepPackageResolver.isLauncherPackage("com.motorola.launcher3"))
    }
}
