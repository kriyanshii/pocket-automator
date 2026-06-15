package com.pocketautomator.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderTextTest {

    @Test
    fun isPlaceholderValue_matchesSpotifySearchHint() {
        assertTrue(PlaceholderText.isPlaceholderValue("What do you want to listen to?"))
    }

    @Test
    fun isPlaceholderValue_rejectsRealQuery() {
        assertFalse(PlaceholderText.isPlaceholderValue("edm playlist"))
    }

    @Test
    fun isPlaceholderValue_rejectsBlank() {
        assertTrue(PlaceholderText.isPlaceholderValue(""))
        assertTrue(PlaceholderText.isPlaceholderValue(null))
    }
}
