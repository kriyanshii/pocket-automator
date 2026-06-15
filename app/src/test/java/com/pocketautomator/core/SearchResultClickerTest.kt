package com.pocketautomator.core

import com.pocketautomator.model.Selector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultClickerTest {

    @Test
    fun isPostSearchResultClick_matchesSpotifyRowRootWithTitle() {
        assertTrue(
            SearchResultClicker.isPostSearchResultClick(
                Selector(
                    resourceId = "com.spotify.music:id/row_root",
                    text = "EDM House Mix",
                    path = listOf(0, 0, 0, 3, 1)
                ),
                "edm playlist"
            )
        )
    }

    @Test
    fun isPostSearchResultClick_matchesMisrecordedWhatsAppEntry() {
        assertTrue(
            SearchResultClicker.isPostSearchResultClick(
                Selector(
                    resourceId = "com.whatsapp:id/entry",
                    text = "Message",
                    className = "android.widget.EditText"
                ),
                "biraj"
            )
        )
    }

    @Test
    fun isPostSearchResultClick_rejectsUnrelatedClick() {
        assertFalse(
            SearchResultClicker.isPostSearchResultClick(
                Selector(resourceId = "com.spotify.music:id/play_button"),
                "edm playlist"
            )
        )
    }

    @Test
    fun isPostSearchResultClick_matchesYouTubePlaylistByContentDescription() {
        assertTrue(
            SearchResultClicker.isPostSearchResultClick(
                Selector(
                    contentDescription = "Playlist - STUDIO GHIBLI FOOD COMPILATION - Savoring Anime: A Culinary Compilation - 9 videos",
                    path = listOf(0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 2, 0),
                    className = "android.widget.Button"
                ),
                "ghibli food"
            )
        )
    }

    @Test
    fun labelsForResultClick_includesContentDescriptionSegments() {
        val labels = SearchResultClicker.labelsForResultClick(
            Selector(
                contentDescription = "Playlist - STUDIO GHIBLI FOOD COMPILATION - Savoring Anime: A Culinary Compilation - 9 videos"
            ),
            "ghibli food"
        )

        assertTrue(labels.contains("ghibli food"))
        assertTrue(labels.any { it.contains("STUDIO GHIBLI FOOD COMPILATION", ignoreCase = true) })
    }
}
