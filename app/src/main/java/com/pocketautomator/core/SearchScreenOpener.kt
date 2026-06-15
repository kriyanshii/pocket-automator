package com.pocketautomator.core

import com.pocketautomator.model.ActionType
import com.pocketautomator.model.Selector

object SearchScreenOpener {

    fun isSearchInputSelector(selector: Selector): Boolean {
        val id = selector.resourceId?.lowercase().orEmpty()
        if (id.contains("search") &&
            (id.contains("input") || id.contains("query") || id.contains("edit_text") ||
                id.endsWith(":id/search"))
        ) {
            return true
        }
        if (id.endsWith(":id/query") || id.endsWith(":id/search_query_text")) {
            return true
        }
        if (selector.className?.contains("EditText", ignoreCase = true) == true &&
            (id.contains("query") || id.contains("search"))
        ) {
            return true
        }
        if (isLocationEditResourceId(id)) {
            return true
        }
        // Apps like LinkedIn often expose search fields without a stable resource id.
        return selector.resourceId.isNullOrBlank() &&
            selector.className?.contains("EditText", ignoreCase = true) == true &&
            !selector.path.isNullOrEmpty()
    }

    fun triggersFor(packageName: String?): List<Selector> {
        val common = listOf(
            Selector(contentDescription = "Search"),
            Selector(text = "Search")
        )
        if (packageName?.contains("whatsapp", ignoreCase = true) == true) {
            return common + listOf(
                Selector(resourceId = "com.whatsapp:id/menuitem_search"),
                Selector(resourceId = "com.whatsapp:id/search"),
                Selector(resourceId = "com.whatsapp:id/search_button"),
                Selector(resourceId = "com.whatsapp:id/search_fragment")
            )
        }
        if (packageName?.contains("linkedin", ignoreCase = true) == true) {
            return common + listOf(
                Selector(resourceId = "com.linkedin.android:id/search_open_bar_box"),
                Selector(resourceId = "com.linkedin.android:id/search_bar"),
                Selector(resourceId = "com.linkedin.android:id/search_edit_text"),
                Selector(resourceId = "com.linkedin.android:id/typeahead_edit_text"),
                Selector(contentDescription = "Search LinkedIn"),
                Selector(contentDescription = "Search for people")
            )
        }
        if (packageName?.contains("spotify", ignoreCase = true) == true) {
            return common + listOf(
                Selector(resourceId = "com.spotify.music:id/search_button"),
                Selector(resourceId = "com.spotify.music:id/search_tab"),
                Selector(resourceId = "com.spotify.music:id/query"),
                Selector(resourceId = "com.spotify.music:id/search_input"),
                Selector(contentDescription = "Search, Tab 2 of 4"),
                Selector(contentDescription = "Search, Tab 3 of 5")
            )
        }
        if (packageName?.contains("youtube", ignoreCase = true) == true) {
            return common + listOf(
                Selector(resourceId = "com.google.android.youtube:id/menu_item_view", contentDescription = "Search"),
                Selector(resourceId = "com.google.android.youtube:id/search")
            )
        }
        if (packageName?.contains("ubercab", ignoreCase = true) == true) {
            return listOf(
                Selector(resourceId = "com.ubercab:id/ub__location_edit_search_container_destination"),
                Selector(resourceId = "com.ubercab:id/ub__location_edit_text_destination"),
                Selector(contentDescription = "Where to?"),
                Selector(text = "Where to?")
            ) + common
        }
        return common
    }

    fun isLocationFieldSelector(selector: Selector): Boolean {
        val id = selector.resourceId?.lowercase().orEmpty()
        return isLocationEditResourceId(id) ||
            id.contains("location") && id.contains("container")
    }

    fun alternateLocationSelectors(selector: Selector): List<Selector> {
        val id = selector.resourceId ?: return locationFieldContentSelectors()
        val alternates = mutableListOf<Selector>()
        if (id.contains("search_container")) {
            alternates += selector.copy(resourceId = id.replace("search_container", "text"))
            alternates += selector.copy(resourceId = id.replace("_search_container_", "_text_"))
        }
        if (id.contains("_container_")) {
            alternates += selector.copy(resourceId = id.replace("_container_", "_text_"))
        }
        alternates += locationFieldContentSelectors()
        return alternates.distinctBy { listOf(it.resourceId, it.contentDescription, it.text) }
    }

    private fun locationFieldContentSelectors(): List<Selector> = listOf(
        Selector(contentDescription = "Where to?"),
        Selector(text = "Where to?"),
        Selector(contentDescription = "Enter destination"),
        Selector(text = "Enter destination")
    )

    private fun isLocationEditResourceId(id: String): Boolean {
        if (id.isBlank()) return false
        val mentionsLocation = id.contains("location") || id.contains("destination") || id.contains("pickup")
        val mentionsInput = id.contains("edit") || id.contains("search") || id.contains("query")
        return mentionsLocation && mentionsInput
    }

    fun tryOpen(
        root: android.view.accessibility.AccessibilityNodeInfo,
        packageName: String?,
        nodeFinder: NodeFinder,
        performClick: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        for (trigger in triggersFor(packageName)) {
            val node = nodeFinder.find(root, trigger, ActionType.CLICK) ?: continue
            val clicked = performClick(node)
            node.recycle()
            if (clicked) return true
        }
        return false
    }
}
