package com.pocketautomator.core

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketautomator.model.ActionType
import com.pocketautomator.model.Selector

class NodeFinder {

    fun find(
        root: AccessibilityNodeInfo?,
        selector: Selector,
        actionType: ActionType? = null
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        if (actionType == ActionType.SET_TEXT && selector.resourceId != null) {
            findSetTextByResourceId(root, selector)?.let { return it }
        }

        if (
            (actionType == ActionType.CLICK || actionType == ActionType.LONG_CLICK) &&
            selector.resourceId != null &&
            selector.text.isNullOrBlank()
        ) {
            findByResourceId(root, selector, actionType)?.let { return it }
        }

        val allowScrollable = actionType == ActionType.SCROLL
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        if (selector.contentDescription != null) {
            collectByContentDesc(root, selector.contentDescription, candidates, allowScrollable, actionType, selector)
            if (candidates.isNotEmpty()) {
                return finalizeFindResult(pickBest(candidates, selector, actionType), actionType, selector)
            }
        }

        if (selector.text != null) {
            val nodes = root.findAccessibilityNodeInfosByText(selector.text)
            for (node in nodes) {
                val target = if (actionType == ActionType.CLICK || actionType == ActionType.LONG_CLICK) {
                    SearchResultClicker.findRowTarget(node) ?: node
                } else {
                    node
                }
                if (isMatch(target, allowScrollable, actionType, selector)) {
                    candidates.add(
                        if (target === node) AccessibilityNodeInfo.obtain(node)
                        else target.also { if (target !== node) node.recycle() }
                    )
                } else {
                    if (target !== node) target.recycle()
                    node.recycle()
                }
            }
            if (candidates.isNotEmpty()) {
                return finalizeFindResult(pickBest(candidates, selector, actionType), actionType, selector)
            }
        }

        if (actionType == ActionType.CLICK || actionType == ActionType.LONG_CLICK) {
            selector.resourceId?.let {
                findByResourceId(root, selector, actionType)?.let { return it }
            }
        }

        selector.path?.let { path ->
            val node = findByPath(root, path)
            if (node != null && isMatch(node, allowScrollable, actionType, selector)) {
                return finalizeFindResult(node, actionType, selector)
            }
            node?.recycle()
        }

        if (actionType == ActionType.SET_TEXT && selector.className != null) {
            findSetTextByClassName(root, selector)?.let { return it }
        }

        if (actionType == ActionType.SET_TEXT && ComposeInputHelper.isComposeBodySelector(selector)) {
            findComposeBodyInput(root, selector)?.let { return it }
        }

        if (actionType == ActionType.SET_TEXT && SearchScreenOpener.isSearchInputSelector(selector)) {
            findSearchInput(root)?.let { return it }
        }

        if (
            (actionType == ActionType.CLICK || actionType == ActionType.LONG_CLICK) &&
            SearchScreenOpener.isLocationFieldSelector(selector)
        ) {
            findLocationField(root, selector, actionType)?.let { return it }
        }

        candidates.forEach { it.recycle() }
        return null
    }

    private fun findLocationField(
        root: AccessibilityNodeInfo,
        selector: Selector,
        actionType: ActionType?
    ): AccessibilityNodeInfo? {
        for (alternate in SearchScreenOpener.alternateLocationSelectors(selector)) {
            findByResourceId(root, alternate, actionType)?.let { node ->
                resolveClickTarget(node)?.let { return it }
                node.recycle()
            }
            alternate.contentDescription?.let { desc ->
                val candidates = mutableListOf<AccessibilityNodeInfo>()
                collectByContentDesc(root, desc, candidates, allowScrollable = false, actionType, alternate)
                if (candidates.isNotEmpty()) {
                    resolveClickTarget(pickBest(candidates, alternate, actionType) ?: return@let)?.let { return it }
                }
            }
            alternate.text?.let { text ->
                val nodes = root.findAccessibilityNodeInfosByText(text)
                for (node in nodes) {
                    if (isMatch(node, allowScrollable = false, actionType, alternate)) {
                        resolveClickTarget(node)?.let { target ->
                            nodes.filter { it !== node }.forEach { it.recycle() }
                            return target
                        }
                    }
                    node.recycle()
                }
            }
        }
        return findLocationEditBySuffix(root, selector, actionType)
    }

    private fun findLocationEditBySuffix(
        root: AccessibilityNodeInfo,
        selector: Selector,
        actionType: ActionType?
    ): AccessibilityNodeInfo? {
        val suffixes = listOfNotNull(
            selector.resourceId?.substringAfterLast('/'),
            "location_edit_text_destination",
            "location_edit_search_container_destination"
        ).distinct()
        for (suffix in suffixes) {
            val candidates = mutableListOf<AccessibilityNodeInfo>()
            collectByResourceIdSuffix(root, suffix, candidates, allowScrollable = false, actionType, selector)
            if (candidates.isNotEmpty()) {
                val best = pickBest(candidates, selector, actionType) ?: continue
                resolveClickTarget(best)?.let { return it }
                best.recycle()
            }
        }
        return null
    }

    private fun resolveClickTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (hasVisibleBounds(node)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        findVisibleClickableDescendant(node)?.let { return it }
        findEditableDescendant(node)?.let { return AccessibilityNodeInfo.obtain(it) }
        return null
    }

    private fun hasVisibleBounds(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return node.isEnabled && bounds.width() > 0 && bounds.height() > 0
    }

    private fun findVisibleClickableDescendant(
        node: AccessibilityNodeInfo,
        maxDepth: Int = 6
    ): AccessibilityNodeInfo? {
        if (maxDepth <= 0) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val target = when {
                child.isClickable && hasVisibleBounds(child) -> AccessibilityNodeInfo.obtain(child)
                else -> findVisibleClickableDescendant(child, maxDepth - 1)
            }
            child.recycle()
            if (target != null) return target
        }
        return null
    }

    private fun findSetTextByResourceId(
        root: AccessibilityNodeInfo,
        selector: Selector
    ): AccessibilityNodeInfo? {
        val id = selector.resourceId ?: return null
        val rawMatches = mutableListOf<AccessibilityNodeInfo>()

        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        rawMatches.addAll(nodes.map { AccessibilityNodeInfo.obtain(it) })
        nodes.forEach { it.recycle() }

        if (rawMatches.isEmpty()) {
            collectNodesByResourceId(root, id, rawMatches)
        }
        if (rawMatches.isEmpty()) {
            collectNodesByResourceIdSuffix(root, id.substringAfterLast('/'), rawMatches)
        }

        val resolved = mutableListOf<AccessibilityNodeInfo>()
        val shellFallbacks = mutableListOf<AccessibilityNodeInfo>()
        for (raw in rawMatches) {
            val target = resolveSetTextTarget(raw)
            if (target != null) {
                resolved.add(target)
                if (target !== raw) raw.recycle()
            } else if (matchesResourceId(raw, id)) {
                shellFallbacks.add(AccessibilityNodeInfo.obtain(raw))
                raw.recycle()
            } else {
                raw.recycle()
            }
        }

        return pickBest(resolved, selector, ActionType.SET_TEXT)
            ?: pickBest(shellFallbacks, selector, ActionType.SET_TEXT)
    }

    private fun collectNodesByResourceId(
        node: AccessibilityNodeInfo,
        resourceId: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.viewIdResourceName != null && matchesResourceId(node, resourceId)) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodesByResourceId(child, resourceId, out)
            child.recycle()
        }
    }

    private fun collectNodesByResourceIdSuffix(
        node: AccessibilityNodeInfo,
        resourceSuffix: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeId = node.viewIdResourceName
        if (nodeId != null && nodeId.substringAfterLast('/') == resourceSuffix) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodesByResourceIdSuffix(child, resourceSuffix, out)
            child.recycle()
        }
    }

    private fun finalizeFindResult(
        node: AccessibilityNodeInfo?,
        actionType: ActionType?,
        selector: Selector? = null
    ): AccessibilityNodeInfo? {
        if (node == null || actionType != ActionType.SET_TEXT) return node
        return resolveSetTextTarget(node)?.also { target ->
            if (target !== node) node.recycle()
        } ?: if (selector != null && matchesResourceId(node, selector.resourceId.orEmpty())) {
            AccessibilityNodeInfo.obtain(node)
        } else {
            node.recycle()
            null
        }
    }

    private fun findSetTextByClassName(
        root: AccessibilityNodeInfo,
        selector: Selector
    ): AccessibilityNodeInfo? {
        val className = selector.className ?: return null
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectByClassName(root, className, candidates)
        if (candidates.isEmpty()) return null

        val filtered = if (selector.resourceId != null) {
            val id = selector.resourceId
            val suffix = id.substringAfterLast('/')
            val matching = candidates.filter { node ->
                node.viewIdResourceName == id ||
                    node.viewIdResourceName?.substringAfterLast('/') == suffix
            }
            if (matching.isNotEmpty()) {
                candidates.filter { it !in matching }.forEach { it.recycle() }
                matching
            } else {
                candidates
            }
        } else {
            candidates
        }

        val resolved = mutableListOf<AccessibilityNodeInfo>()
        val shellFallbacks = mutableListOf<AccessibilityNodeInfo>()
        for (candidate in filtered) {
            val target = resolveSetTextTarget(candidate)
            if (target != null) {
                resolved.add(target)
                if (target !== candidate) candidate.recycle()
            } else if (matchesResourceId(candidate, selector.resourceId.orEmpty())) {
                shellFallbacks.add(AccessibilityNodeInfo.obtain(candidate))
                candidate.recycle()
            } else {
                candidate.recycle()
            }
        }
        return pickBest(resolved, selector, ActionType.SET_TEXT)
            ?: pickBest(shellFallbacks, selector, ActionType.SET_TEXT)
    }

    private fun findComposeBodyInput(
        root: AccessibilityNodeInfo,
        selector: Selector
    ): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectEditableInputs(root, candidates)
        collectComposeWebViews(root, candidates)
        if (candidates.isEmpty()) return null

        val suffix = selector.resourceId?.substringAfterLast('/')?.lowercase().orEmpty()
        val chosen = candidates.maxByOrNull { scoreComposeBody(it, suffix) } ?: candidates.first()
        candidates.filter { it !== chosen }.forEach { it.recycle() }
        return chosen
    }

    private fun collectComposeWebViews(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val className = node.className?.toString().orEmpty()
        if (className.contains("WebView", ignoreCase = true) && node.isEnabled) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                out.add(AccessibilityNodeInfo.obtain(node))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectComposeWebViews(child, out)
            child.recycle()
        }
    }

    private fun scoreComposeBody(node: AccessibilityNodeInfo, suffix: String): Int {
        var score = 0
        if (node.isFocused) score += 12
        val id = node.viewIdResourceName.orEmpty().lowercase()
        if (id.endsWith("/$suffix") || id == suffix) score += 8
        if (id.contains("editor") || id.contains("body")) score += 5
        if (node.isEditable) score += 4
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        score += (bounds.height() / 100).coerceAtMost(10)
        return score
    }

    private fun matchesResourceId(node: AccessibilityNodeInfo, resourceId: String): Boolean {
        val nodeId = node.viewIdResourceName ?: return false
        if (nodeId == resourceId) return true
        return nodeId.substringAfterLast('/') == resourceId.substringAfterLast('/')
    }

    private fun collectByClassName(
        node: AccessibilityNodeInfo,
        className: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString() == className && node.isEnabled) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                out.add(AccessibilityNodeInfo.obtain(node))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectByClassName(child, className, out)
            child.recycle()
        }
    }

    fun findSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectEditableInputs(root, candidates)
        if (candidates.isEmpty()) return null

        val chosen = candidates.maxByOrNull { scoreSearchInput(it) } ?: candidates.first()
        candidates.filter { it !== chosen }.forEach { it.recycle() }
        return chosen
    }

    private fun collectEditableInputs(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (isDirectTextInput(node)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (node.isEnabled && bounds.width() > 0 && bounds.height() > 0) {
                out.add(AccessibilityNodeInfo.obtain(node))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectEditableInputs(child, out)
            child.recycle()
        }
    }

    private fun scoreSearchInput(node: AccessibilityNodeInfo): Int {
        var score = 0
        if (node.isFocused) score += 10
        val id = node.viewIdResourceName.orEmpty().lowercase()
        if (id.contains("search") || id.contains("typeahead")) score += 5
        val desc = node.contentDescription?.toString().orEmpty().lowercase()
        if (desc.contains("search")) score += 3
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hint = node.hintText?.toString().orEmpty().lowercase()
            if (hint.contains("search")) score += 3
        }
        if (node.isEditable) score += 2
        return score
    }

    private fun findByResourceId(
        root: AccessibilityNodeInfo,
        selector: Selector,
        actionType: ActionType?
    ): AccessibilityNodeInfo? {
        val id = selector.resourceId ?: return null
        val allowScrollable = actionType == ActionType.SCROLL
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        candidates.addAll(nodes.filter { isMatch(it, allowScrollable, actionType, selector) })
        nodes.filter { it !in candidates }.forEach { it.recycle() }

        if (candidates.isEmpty()) {
            collectByResourceId(root, id, candidates, allowScrollable, actionType, selector)
        }

        if (candidates.isEmpty()) {
            collectByResourceIdSuffix(root, id.substringAfterLast('/'), candidates, allowScrollable, actionType, selector)
        }

        val filtered = filterBySelectorText(candidates, selector, actionType)
        return pickBest(filtered, selector, actionType)
    }

    private fun filterBySelectorText(
        candidates: List<AccessibilityNodeInfo>,
        selector: Selector,
        actionType: ActionType?
    ): List<AccessibilityNodeInfo> {
        val text = selector.text ?: return candidates
        if (actionType != ActionType.CLICK && actionType != ActionType.LONG_CLICK) return candidates
        val matching = candidates.filter { nodeContainsText(it, text) }
        if (matching.isEmpty()) return candidates
        candidates.filter { it !in matching }.forEach { it.recycle() }
        return matching
    }

    private fun nodeContainsText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return true
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) return true
        return findDescendantText(node, maxDepth = 4)?.contains(text, ignoreCase = true) == true
    }

    private fun findDescendantText(node: AccessibilityNodeInfo, maxDepth: Int): String? {
        if (maxDepth <= 0) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = child.text?.toString()
                ?: child.contentDescription?.toString()
                ?: findDescendantText(child, maxDepth - 1)
            child.recycle()
            if (!found.isNullOrBlank()) return found
        }
        return null
    }

    private fun collectByResourceId(
        node: AccessibilityNodeInfo,
        resourceId: String,
        out: MutableList<AccessibilityNodeInfo>,
        allowScrollable: Boolean,
        actionType: ActionType?,
        selector: Selector
    ) {
        if (node.viewIdResourceName == resourceId && isMatch(node, allowScrollable, actionType, selector)) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectByResourceId(child, resourceId, out, allowScrollable, actionType, selector)
            child.recycle()
        }
    }

    private fun collectByResourceIdSuffix(
        node: AccessibilityNodeInfo,
        resourceSuffix: String,
        out: MutableList<AccessibilityNodeInfo>,
        allowScrollable: Boolean,
        actionType: ActionType?,
        selector: Selector
    ) {
        val nodeId = node.viewIdResourceName
        if (nodeId != null && nodeId.substringAfterLast('/') == resourceSuffix &&
            isMatch(node, allowScrollable, actionType, selector)
        ) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectByResourceIdSuffix(child, resourceSuffix, out, allowScrollable, actionType, selector)
            child.recycle()
        }
    }

    private fun collectByContentDesc(
        root: AccessibilityNodeInfo,
        desc: String,
        out: MutableList<AccessibilityNodeInfo>,
        allowScrollable: Boolean,
        actionType: ActionType?,
        selector: Selector
    ) {
        val target = desc.lowercase()
        collectMatching(root, out, allowScrollable, actionType, selector) { node ->
            node.contentDescription?.toString()?.lowercase() == target
        }
        if (out.isEmpty() && target.length >= 16) {
            collectMatching(root, out, allowScrollable, actionType, selector) { node ->
                val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: return@collectMatching false
                nodeDesc.contains(target) || target.contains(nodeDesc)
            }
        }
    }

    private fun collectMatching(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        allowScrollable: Boolean,
        actionType: ActionType?,
        selector: Selector,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ) {
        if (predicate(node) && isMatch(node, allowScrollable, actionType, selector)) {
            out.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectMatching(child, out, allowScrollable, actionType, selector, predicate)
            child.recycle()
        }
    }

    private fun findByPath(root: AccessibilityNodeInfo, path: List<Int>): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo = root
        for (index in path) {
            if (index < 0 || index >= current.childCount) {
                if (current !== root) current.recycle()
                return null
            }
            val child = current.getChild(index) ?: run {
                if (current !== root) current.recycle()
                return null
            }
            if (current !== root) current.recycle()
            current = child
        }
        return AccessibilityNodeInfo.obtain(current)
    }

    private fun isMatch(
        node: AccessibilityNodeInfo,
        allowScrollable: Boolean,
        actionType: ActionType?,
        selector: Selector
    ): Boolean {
        if (allowScrollable && AccessibilityNodeMatchers.isScrollContainer(node)) return true
        if (!node.isEnabled) return false
        if (actionType == ActionType.SET_TEXT && !isTextInputTarget(node, selector)) return false
        if (
            (actionType == ActionType.CLICK || actionType == ActionType.LONG_CLICK) &&
            selector.text != null &&
            isPlaceholderText(selector.text) &&
            !node.isClickable
        ) {
            return false
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds.width() > 0 && bounds.height() > 0
    }

    private fun isPlaceholderText(text: String): Boolean {
        return PLACEHOLDER_TEXT.any { it.equals(text, ignoreCase = true) }
    }

    private val PLACEHOLDER_TEXT = setOf(
        "Message",
        "Search",
        "Type a message",
        "Search name or number"
    )

    private fun isTextInputTarget(node: AccessibilityNodeInfo, selector: Selector): Boolean {
        return isDirectTextInput(node)
    }

    private fun isDirectTextInput(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        if (className.contains("ImageView", ignoreCase = true) ||
            className.contains("ImageButton", ignoreCase = true)
        ) {
            return false
        }
        if (node.isEditable) return true
        if (className.contains("WebView", ignoreCase = true) && node.isFocusable) return true
        return className.contains("EditText", ignoreCase = true) ||
            className.contains("AutoComplete", ignoreCase = true)
    }

    private fun resolveSetTextTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isDirectTextInput(node)) return AccessibilityNodeInfo.obtain(node)
        return findEditableDescendant(node)?.let { AccessibilityNodeInfo.obtain(it) }
    }

    private fun findEditableDescendant(node: AccessibilityNodeInfo, maxDepth: Int = 6): AccessibilityNodeInfo? {
        if (isDirectTextInput(node)) return node
        if (maxDepth <= 0) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableDescendant(child, maxDepth - 1)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun pickBest(
        candidates: List<AccessibilityNodeInfo>,
        selector: Selector,
        actionType: ActionType?
    ): AccessibilityNodeInfo? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        val chosen = candidates.maxByOrNull { scoreCandidate(it, selector, actionType) } ?: candidates.first()
        candidates.filter { it !== chosen }.forEach { it.recycle() }
        return chosen
    }

    private fun scoreCandidate(
        node: AccessibilityNodeInfo,
        selector: Selector,
        actionType: ActionType?
    ): Int {
        var score = 0

        selector.text?.let { text ->
            val nodeText = node.text?.toString()
            when {
                nodeText == text -> score += 5
                nodeText?.contains(text) == true -> score += 2
            }
        }

        selector.contentDescription?.let { desc ->
            if (node.contentDescription?.toString()?.equals(desc, ignoreCase = true) == true) {
                score += 3
            }
        }

        selector.className?.let { className ->
            if (node.className?.toString() == className) score += 2
        }

        selector.resourceId?.let { id ->
            if (matchesResourceId(node, id)) score += 5
        }

        when (actionType) {
            ActionType.CLICK, ActionType.LONG_CLICK -> if (node.isClickable) score += 3
            ActionType.SCROLL -> if (AccessibilityNodeMatchers.isScrollContainer(node)) score += 3
            ActionType.SET_TEXT -> {
                if (node.isEditable) score += 10
                if (node.className?.toString()?.contains("EditText", ignoreCase = true) == true) score += 8
                if (isDirectTextInput(node)) score += 5
            }
            else -> Unit
        }

        return score
    }
}
