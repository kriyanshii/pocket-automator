package com.pocketautomator.core

import com.pocketautomator.model.Selector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExtractorTest {

    @Test
    fun buildSelector_prefersResourceIdOverText() {
        val selector = ActionExtractor.buildSelectorFromSnapshot(
            resourceId = "com.spotify:id/play",
            contentDescription = "Play",
            text = "Play",
            path = listOf(0, 2, 1)
        )
        assertEquals("com.spotify:id/play", selector.resourceId)
        assertEquals("Play", selector.contentDescription)
        assertEquals("Play", selector.text)
        assertEquals(listOf(0, 2, 1), selector.path)
    }

    @Test
    fun buildSelector_stripsGenericAndroidIdWhenBetterOptionsExist() {
        val selector = ActionExtractor.buildSelectorFromSnapshot(
            resourceId = "android:id/content",
            contentDescription = "Main content",
            text = null,
            path = listOf(0)
        )
        assertNull(selector.resourceId)
        assertEquals("Main content", selector.contentDescription)
    }

    @Test
    fun buildSelector_keepsAndroidIdWhenOnlyOption() {
        val selector = ActionExtractor.buildSelectorFromSnapshot(
            resourceId = "android:id/content",
            contentDescription = null,
            text = null,
            path = listOf(0)
        )
        assertEquals("android:id/content", selector.resourceId)
    }

    @Test
    fun buildSelector_emptyPathBecomesNull() {
        val selector = ActionExtractor.buildSelectorFromSnapshot(
            resourceId = "app:id/btn",
            contentDescription = null,
            text = "OK",
            path = emptyList()
        )
        assertNull(selector.path)
    }

    @Test
    fun buildSelector_stripsUnstableGeneratedResourceIds() {
        val selector = ActionExtractor.buildSelectorFromSnapshot(
            resourceId = "com.spotify.music:id/item_32423",
            contentDescription = "EDM Playlist",
            text = "EDM",
            path = listOf(0, 1)
        )
        assertNull(selector.resourceId)
        assertEquals("EDM Playlist", selector.contentDescription)
    }

    @Test
    fun buildSelector_keepsStableResourceIds() {
        val selector = ActionExtractor.buildSelectorFromSnapshot(
            resourceId = "com.spotify.music:id/playlist_row",
            contentDescription = null,
            text = "EDM",
            path = listOf(0, 1)
        )
        assertEquals("com.spotify.music:id/playlist_row", selector.resourceId)
    }

    @Test
    fun buildSelectorFromSnapshot_stableListIdsDropPathWhenUsedInScroll() {
        val selector = ActionExtractor.buildSelectorFromSnapshot(
            resourceId = "com.whatsapp:id/updates_list",
            contentDescription = null,
            text = null,
            path = listOf(0, 0, 0, 1),
            className = "androidx.recyclerview.widget.RecyclerView"
        )
        assertEquals("com.whatsapp:id/updates_list", selector.resourceId)
        assertTrue(AccessibilityNodeMatchers.isStableListResourceId(selector.resourceId!!))
    }
}
