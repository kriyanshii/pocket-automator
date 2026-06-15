package com.pocketautomator.core

import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityNodeMatchers {

    fun isScrollContainer(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable) return true

        val className = node.className?.toString().orEmpty()
        if (className.contains("RecyclerView", ignoreCase = true) ||
            className.contains("ScrollView", ignoreCase = true) ||
            className.contains("ListView", ignoreCase = true) ||
            className.contains("ViewPager", ignoreCase = true)
        ) {
            return true
        }

        val resourceId = node.viewIdResourceName?.lowercase().orEmpty()
        return "recycler" in resourceId ||
            "scroll" in resourceId ||
            resourceId.endsWith("_list") ||
            ":id/list" in resourceId
    }

    fun isStableListResourceId(resourceId: String): Boolean {
        val suffix = resourceId.substringAfterLast('/').lowercase()
        return suffix.endsWith("_list") ||
            "recycler" in suffix ||
            "scroll" in suffix ||
            suffix == "list"
    }
}
