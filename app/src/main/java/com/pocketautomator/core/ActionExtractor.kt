package com.pocketautomator.core

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketautomator.model.Action
import com.pocketautomator.model.ActionType
import com.pocketautomator.model.Selector

object ActionExtractor {

    fun extract(event: AccessibilityEvent): Action? {
        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> extractClick(event, ActionType.CLICK)
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> extractClick(event, ActionType.LONG_CLICK)
            AccessibilityEvent.TYPE_VIEW_SELECTED -> extractSelectedAsClick(event)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> extractSetText(event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> extractScroll(event)
            else -> null
        }
    }

    private fun extractClick(event: AccessibilityEvent, type: ActionType): Action? {
        val source = event.source ?: return null
        val target = findClickableTarget(source)
        val path = computePathFromRoot(target)
        val selector = buildSelector(target, path)
        recycleIfDistinct(source, target)
        return Action(type = type, selector = selector)
    }

    private fun extractSelectedAsClick(event: AccessibilityEvent): Action? {
        val source = event.source ?: return null
        val target = findClickableTarget(source)
        val resourceId = target.viewIdResourceName.orEmpty()
        if (!isListRowResourceId(resourceId)) {
            recycleIfDistinct(source, target)
            return null
        }
        val path = computePathFromRoot(target)
        val selector = buildSelector(target, path)
        recycleIfDistinct(source, target)
        return Action(type = ActionType.CLICK, selector = selector)
    }

    private fun extractSetText(event: AccessibilityEvent): Action? {
        val node = event.source ?: return null
        val path = computePathFromRoot(node)
        val selector = buildInputSelector(node, path)
        val raw = event.text.firstOrNull()?.toString()
            ?: node.text?.toString()
        node.recycle()
        val text = TextNormalizer.normalize(raw) ?: return null
        return Action(type = ActionType.SET_TEXT, selector = selector, value = text)
    }

    private fun extractScroll(event: AccessibilityEvent): Action? {
        if (event.scrollX == 0 && event.scrollY == 0) return null

        val source = event.source ?: return null
        val target = findScrollableTarget(source)
        val path = computePathFromRoot(target)
        val selector = buildSelector(target, path).withoutFragilePath()
        val direction = scrollDirection(event)
        recycleIfDistinct(source, target)
        return Action(type = ActionType.SCROLL, selector = selector, value = direction)
    }

    private fun scrollDirection(event: AccessibilityEvent): String {
        return when {
            event.scrollY > 0 || event.scrollX > 0 -> "forward"
            event.scrollY < 0 || event.scrollX < 0 -> "backward"
            else -> "forward"
        }
    }

    private fun findClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (isActionable(node)) return AccessibilityNodeInfo.obtain(node)
        var current = node.parent
        while (current != null) {
            if (isActionable(current) || isListRowResourceId(current.viewIdResourceName.orEmpty())) {
                return AccessibilityNodeInfo.obtain(current)
            }
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return AccessibilityNodeInfo.obtain(node)
    }

    private fun isActionable(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return true
        return node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
    }

    private fun isListRowResourceId(resourceId: String): Boolean {
        if (resourceId.isBlank()) return false
        val suffix = resourceId.substringAfterLast('/').lowercase()
        return suffix.contains("row") ||
            suffix.contains("contact") ||
            suffix.contains("conversation") ||
            suffix.contains("list_item")
    }

    private fun findScrollableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (AccessibilityNodeMatchers.isScrollContainer(node)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        var current = node.parent
        while (current != null) {
            if (AccessibilityNodeMatchers.isScrollContainer(current)) {
                return AccessibilityNodeInfo.obtain(current)
            }
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return AccessibilityNodeInfo.obtain(node)
    }

    private fun recycleIfDistinct(source: AccessibilityNodeInfo, target: AccessibilityNodeInfo) {
        if (source !== target) source.recycle()
        target.recycle()
    }

    private fun computePathFromRoot(node: AccessibilityNodeInfo): List<Int> {
        val path = mutableListOf<Int>()
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        while (current != null) {
            val parent = current.parent
            if (parent == null) break
            val index = findChildIndex(parent, current)
            if (index >= 0) path.add(0, index)
            val toRecycle = current
            current = parent
            toRecycle.recycle()
        }
        current?.recycle()
        return path
    }

    private fun findChildIndex(parent: AccessibilityNodeInfo, child: AccessibilityNodeInfo): Int {
        for (i in 0 until parent.childCount) {
            val c = parent.getChild(i) ?: continue
            val matches = c == child
            if (c !== child) c.recycle()
            if (matches) return i
        }
        return -1
    }

    fun buildInputSelector(node: AccessibilityNodeInfo, path: List<Int> = emptyList()): Selector {
        val selector = buildSelector(node, path).copy(text = null)
        val resourceId = selector.resourceId ?: return selector
        return if (!isUnstableResourceId(resourceId)) selector.copy(path = null) else selector
    }

    fun buildSelector(node: AccessibilityNodeInfo, path: List<Int> = emptyList()): Selector {
        val resourceId = node.viewIdResourceName?.takeIf { it.isNotBlank() }
        val contentDescription = TextNormalizer.normalize(node.contentDescription?.toString())
        val ownText = TextNormalizer.normalize(node.text?.toString())
        val text = ownText ?: descendantTextForRow(node, resourceId)
        val className = node.className?.toString()?.takeIf { it.isNotBlank() }

        return Selector(
            resourceId = resourceId?.takeUnless {
                isUnstableResourceId(it) || (isGenericAndroidId(it) && (contentDescription != null || text != null))
            },
            contentDescription = contentDescription,
            text = text?.takeUnless { isPlaceholderClickText(it, resourceId) },
            path = path.takeIf { it.isNotEmpty() },
            className = className
        )
    }

    private fun isPlaceholderClickText(text: String, resourceId: String?): Boolean {
        if (resourceId == null) return false
        val suffix = resourceId.substringAfterLast('/').lowercase()
        if (suffix != "entry" && suffix != "search_input" && !suffix.contains("input")) return false
        return text.equals("Message", ignoreCase = true) ||
            text.equals("Search", ignoreCase = true) ||
            text.startsWith("Type a", ignoreCase = true) ||
            text.startsWith("Search ", ignoreCase = true)
    }

    private fun descendantTextForRow(node: AccessibilityNodeInfo, resourceId: String?): String? {
        if (!isListRowResourceId(resourceId.orEmpty())) return null
        return findDescendantText(node)
    }

    private fun findDescendantText(node: AccessibilityNodeInfo, maxDepth: Int = 4): String? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = TextNormalizer.normalize(child.text?.toString())
                ?: if (maxDepth > 1) findDescendantText(child, maxDepth - 1) else null
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    fun buildSelectorFromSnapshot(
        resourceId: String?,
        contentDescription: String?,
        text: String?,
        path: List<Int>,
        className: String? = null
    ): Selector {
        return Selector(
            resourceId = resourceId?.takeUnless {
                isUnstableResourceId(it) || (isGenericAndroidId(it) && (contentDescription != null || text != null))
            },
            contentDescription = TextNormalizer.normalize(contentDescription),
            text = TextNormalizer.normalize(text),
            path = path.takeIf { it.isNotEmpty() },
            className = className
        )
    }

    private fun isGenericAndroidId(resourceId: String): Boolean {
        return resourceId.startsWith("android:id/")
    }

    private fun isUnstableResourceId(resourceId: String): Boolean {
        val suffix = resourceId.substringAfterLast('/')
        return suffix.matches(Regex(""".+\d{3,}"""))
    }

    private fun Selector.withoutFragilePath(): Selector {
        val id = resourceId ?: return this
        return if (AccessibilityNodeMatchers.isStableListResourceId(id)) copy(path = null) else this
    }
}
