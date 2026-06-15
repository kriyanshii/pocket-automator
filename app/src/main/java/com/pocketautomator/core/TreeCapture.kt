package com.pocketautomator.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketautomator.model.ScreenNode

class TreeCapture {
    private var nextId = 1

    fun capture(root: AccessibilityNodeInfo?): ScreenNode {
        nextId = 1
        if (root == null) {
            return ScreenNode(nodeId = "0")
        }
        return traverse(root, depth = 0) ?: ScreenNode(nodeId = "0")
    }

    private fun traverse(node: AccessibilityNodeInfo, depth: Int): ScreenNode? {
        if (depth > MAX_DEPTH) return null

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!shouldIncludeNode(node, bounds)) {
            return null
        }

        val nodeId = (nextId++).toString()
        val children = mutableListOf<ScreenNode>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childNode = traverse(child, depth + 1)
            child.recycle()
            if (childNode != null) {
                children.add(childNode)
            }
        }

        return ScreenNode(
            nodeId = nodeId,
            text = node.text?.toString()?.takeIf { it.isNotBlank() },
            resourceId = node.viewIdResourceName?.takeIf { it.isNotBlank() },
            contentDescription = node.contentDescription?.toString()?.takeIf { it.isNotBlank() },
            className = node.className?.toString(),
            clickable = node.isClickable,
            enabled = node.isEnabled,
            bounds = formatBounds(bounds),
            children = children
        )
    }

    private fun shouldIncludeNode(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        val hasText = !node.text.isNullOrBlank()
        val hasDesc = !node.contentDescription.isNullOrBlank()
        val hasId = !node.viewIdResourceName.isNullOrBlank()
        val isInteractive = node.isClickable || node.isFocusable || node.isEditable ||
            node.isScrollable
        return hasText || hasDesc || hasId || isInteractive || node.childCount > 0
    }

    private fun formatBounds(bounds: Rect): String {
        return "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]"
    }

    companion object {
        private const val MAX_DEPTH = 15
    }
}
