package com.pocketautomator.core

import com.pocketautomator.model.Selector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchScreenOpenerTest {

    @Test
    fun isSearchInputSelector_matchesWhatsAppSearchInput() {
        assertTrue(
            SearchScreenOpener.isSearchInputSelector(
                Selector(resourceId = "com.whatsapp:id/search_input")
            )
        )
    }

    @Test
    fun isSearchInputSelector_matchesPathOnlyEditText() {
        assertTrue(
            SearchScreenOpener.isSearchInputSelector(
                Selector(
                    className = "android.widget.EditText",
                    path = listOf(0, 0, 0, 1)
                )
            )
        )
    }

    @Test
    fun isSearchInputSelector_matchesSpotifyQueryField() {
        assertTrue(
            SearchScreenOpener.isSearchInputSelector(
                Selector(resourceId = "com.spotify.music:id/query")
            )
        )
    }

    @Test
    fun isSearchInputSelector_matchesYouTubeSearchEditText() {
        assertTrue(
            SearchScreenOpener.isSearchInputSelector(
                Selector(resourceId = "com.google.android.youtube:id/search_edit_text")
            )
        )
    }

    @Test
    fun isSearchInputSelector_rejectsUnrelatedIds() {
        assertFalse(
            SearchScreenOpener.isSearchInputSelector(
                Selector(resourceId = "com.whatsapp:id/contact_row_container")
            )
        )
    }

    @Test
    fun isSearchInputSelector_matchesUberDestinationField() {
        assertTrue(
            SearchScreenOpener.isSearchInputSelector(
                Selector(resourceId = "com.ubercab:id/ub__location_edit_text_destination")
            )
        )
    }

    @Test
    fun isLocationFieldSelector_matchesUberDestinationContainer() {
        assertTrue(
            SearchScreenOpener.isLocationFieldSelector(
                Selector(resourceId = "com.ubercab:id/ub__location_edit_search_container_destination")
            )
        )
    }

    @Test
    fun alternateLocationSelectors_includesEditTextVariant() {
        val alternates = SearchScreenOpener.alternateLocationSelectors(
            Selector(resourceId = "com.ubercab:id/ub__location_edit_search_container_destination")
        )

        assertTrue(
            alternates.any {
                it.resourceId == "com.ubercab:id/ub__location_edit_text_destination"
            }
        )
    }
}
