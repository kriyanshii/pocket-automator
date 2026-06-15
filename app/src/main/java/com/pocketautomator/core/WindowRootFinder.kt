package com.pocketautomator.core

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

object WindowRootFinder {

    fun findAllRootsForPackage(
        packageName: String?,
        windows: List<AccessibilityWindowInfo>,
        activeRootProvider: () -> AccessibilityNodeInfo?
    ): List<AccessibilityNodeInfo> {
        if (packageName.isNullOrBlank()) {
            return activeRootProvider()?.let { listOf(obtainCopy(it)) } ?: emptyList()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val includeSystemWindows = packageName == SYSTEM_UI_PACKAGE
            val matching = windows
                .filter { window ->
                    window.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                        (includeSystemWindows || window.type != AccessibilityWindowInfo.TYPE_SYSTEM)
                }
                .mapNotNull { window ->
                    val root = window.root ?: return@mapNotNull null
                    if (root.packageName?.toString() == packageName) {
                        window to root
                    } else {
                        root.recycle()
                        null
                    }
                }
                .sortedWith(
                    compareByDescending<Pair<AccessibilityWindowInfo, AccessibilityNodeInfo>> { (window, _) ->
                        window.isActive
                    }.thenByDescending { (window, _) -> window.layer }
                )

            if (matching.isNotEmpty()) {
                return matching.map { (_, root) -> obtainCopy(root) }
            }
        }

        val active = activeRootProvider()
        return if (active?.packageName?.toString() == packageName) {
            listOf(obtainCopy(active))
        } else {
            active?.recycle()
            emptyList()
        }
    }

    fun findRootForPackage(
        packageName: String?,
        windows: List<AccessibilityWindowInfo>,
        activeRootProvider: () -> AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        return findAllRootsForPackage(packageName, windows, activeRootProvider).firstOrNull()
    }

    private fun obtainCopy(node: AccessibilityNodeInfo): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain(node)

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
}
