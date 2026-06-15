package com.pocketautomator.core

import android.view.accessibility.AccessibilityNodeInfo
import com.pocketautomator.model.Selector

object SearchResultClicker {

    private val SEARCH_RESULT_ROW_SUFFIXES = listOf(
        "contact_row_container",
        "conversations_row",
        "search_entity_result",
        "search_result_row",
        "entity_result",
        "row_root",
        "playlist_row",
        "search_result"
    )

    fun selectorResultLabel(selector: Selector): String? =
        selector.text?.takeIf { it.isNotBlank() }
            ?: selector.contentDescription?.takeIf { it.isNotBlank() }

    fun labelsForResultClick(selector: Selector, query: String): List<String> {
        val labels = mutableListOf<String>()
        selector.text?.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        selector.contentDescription?.takeIf { it.isNotBlank() }?.let { desc ->
            labels.add(desc)
            labels.addAll(extractResultLabels(desc))
        }
        query.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        return labels.distinct().filter { it.isNotBlank() }
    }

    fun clickResult(
        root: AccessibilityNodeInfo,
        query: String,
        performClick: (AccessibilityNodeInfo) -> Boolean,
        resultLabel: String? = null
    ): Boolean {
        val labels = buildList {
            resultLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
            query.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()
        if (labels.isEmpty()) return false

        for (label in labels) {
            if (clickByTextLabel(root, label, performClick)) return true
        }

        return clickFirstSearchResultRow(root, performClick)
    }

    fun clickResult(
        root: AccessibilityNodeInfo,
        selector: Selector,
        query: String,
        performClick: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        for (label in labelsForResultClick(selector, query)) {
            if (clickByTextLabel(root, label, performClick)) return true
        }
        return clickFirstSearchResultRow(root, performClick)
    }

    private fun clickByTextLabel(
        root: AccessibilityNodeInfo,
        label: String,
        performClick: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(label).toMutableList()
        collectNodesMatchingLabel(root, label, nodes)
        for (node in nodes) {
            val target = findRowTarget(node) ?: continue
            val clicked = performClick(target)
            target.recycle()
            if (clicked) {
                nodes.filter { it !== node }.forEach { it.recycle() }
                node.recycle()
                return true
            }
        }
        nodes.forEach { it.recycle() }
        return false
    }

    private fun collectNodesMatchingLabel(
        node: AccessibilityNodeInfo,
        label: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        val needle = label.lowercase()
        val nodeText = node.text?.toString()?.lowercase()
        val nodeDesc = node.contentDescription?.toString()?.lowercase()
        if ((nodeText != null && nodeText.contains(needle)) ||
            (nodeDesc != null && (nodeDesc.contains(needle) || needle.contains(nodeDesc)))
        ) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodesMatchingLabel(child, label, out)
            child.recycle()
        }
    }

    private fun extractResultLabels(contentDescription: String): List<String> {
        return contentDescription.split(" - ")
            .map { it.trim() }
            .filter { it.length >= 8 }
            .filterNot { it.matches(Regex("^\\d+ videos?$", RegexOption.IGNORE_CASE)) }
            .filterNot { it.equals("Playlist", ignoreCase = true) }
            .filterNot { it.equals("Video", ignoreCase = true) }
    }

    private fun clickFirstSearchResultRow(
        root: AccessibilityNodeInfo,
        performClick: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        for (suffix in SEARCH_RESULT_ROW_SUFFIXES) {
            val row = findNodeByResourceSuffix(root, suffix) ?: continue
            val clicked = performClick(row)
            row.recycle()
            if (clicked) return true
        }
        return false
    }

    private fun findNodeByResourceSuffix(
        node: AccessibilityNodeInfo,
        suffix: String
    ): AccessibilityNodeInfo? {
        val id = node.viewIdResourceName
        if (id != null && id.substringAfterLast('/') == suffix) {
            val target = findRowTarget(node) ?: return AccessibilityNodeInfo.obtain(node)
            return target
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByResourceSuffix(child, suffix)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    fun findRowTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        while (current != null) {
            val id = current.viewIdResourceName.orEmpty()
            if (SEARCH_RESULT_ROW_SUFFIXES.any { suffix ->
                    id.endsWith("/$suffix") || id.contains(suffix)
                } || id.contains("contact_row") || id.contains("row_container") ||
                id.contains("conversations_row") || id.contains("search_entity") ||
                id.contains("entity_result") || id.contains("search_result")
            ) {
                return current
            }
            if (current.isClickable) {
                return current
            }
            val parent = current.parent
            if (current !== node) current.recycle()
            current = parent
        }
        return null
    }

    fun isPostSearchResultClick(
        clickSelector: Selector,
        previousSearchQuery: String?
    ): Boolean {
        if (previousSearchQuery.isNullOrBlank()) return false
        if (isMisrecordedEntryClickAfterSearch(clickSelector, previousSearchQuery)) return true
        val label = selectorResultLabel(clickSelector)
        val id = clickSelector.resourceId?.substringAfterLast('/').orEmpty().lowercase()
        if (id in SEARCH_RESULT_ROW_SUFFIXES || id.contains("row")) {
            return !label.isNullOrBlank()
        }
        return !label.isNullOrBlank() && !clickSelector.path.isNullOrEmpty()
    }

    fun isMisrecordedEntryClickAfterSearch(
        clickSelector: Selector,
        previousSearchQuery: String?
    ): Boolean {
        if (previousSearchQuery.isNullOrBlank()) return false
        val id = clickSelector.resourceId?.substringAfterLast('/').orEmpty()
        if (id == "entry" || clickSelector.text.equals("Message", ignoreCase = true)) return true
        if (clickSelector.className?.contains("EditText", ignoreCase = true) == true) return true
        return false
    }
}
