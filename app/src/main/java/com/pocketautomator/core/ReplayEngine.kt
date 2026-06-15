package com.pocketautomator.core

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketautomator.model.ActionType
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.Selector
import kotlinx.coroutines.delay

data class ReplayStepResult(
    val stepIndex: Int,
    val success: Boolean,
    val message: String
)

private data class NodeWaitResult(
    val root: AccessibilityNodeInfo?,
    val node: AccessibilityNodeInfo?,
    val hadWindow: Boolean
)

class ReplayEngine(
    private val nodeFinder: NodeFinder,
    private val rootsProvider: (String?) -> List<AccessibilityNodeInfo>,
    private val navigator: ((String) -> Boolean)? = null,
    private val labelResolver: ((String) -> String?)? = null,
    private val keyboardDismisser: (() -> Unit)? = null
) {
    var onStepResult: ((ReplayStepResult) -> Unit)? = null
    var onComplete: ((Boolean, String) -> Unit)? = null

    @Volatile
    var isReplaying: Boolean = false
        private set

    suspend fun replay(recording: ExportRecording) {
        if (isReplaying) return
        isReplaying = true

        try {
            if (recording.steps.isEmpty()) {
                onComplete?.invoke(false, "Recording has no steps")
                return
            }

            val plan = ReplayPlanner.plan(recording)
            var lastPackage: String? = null

            if (plan.skippedLauncherSteps > 0) {
                onStepResult?.invoke(
                    ReplayStepResult(
                        stepIndex = -1,
                        success = true,
                        message = "Skipped ${plan.skippedLauncherSteps} launcher step(s), opening ${plan.initialLaunchPackage}"
                    )
                )
            }

            if (plan.initialLaunchPackage != null) {
                if (!ensureForeground(plan.initialLaunchPackage)) {
                    onComplete?.invoke(
                        false,
                        "Could not launch ${plan.initialLaunchPackage} — open it manually, then replay"
                    )
                    return
                }
                lastPackage = plan.initialLaunchPackage
            }

            plan.steps.forEachIndexed { stepIndex, planned ->
                delay(STEP_DELAY_MS)
                val index = planned.originalIndex
                val step = planned.step
                val nextStep = plan.steps.getOrNull(stepIndex + 1)?.step
                val stepPackage = StepPackageResolver.resolve(step)
                    ?: recording.app.takeIf { it.isNotBlank() }

                if (ReplayPlanner.isLauncherAppLaunch(step, stepPackage)) {
                    val launchPackage = recording.app.takeIf { it.isNotBlank() }
                        ?: step.action.text?.let { labelResolver?.invoke(it) }
                        ?: step.action.contentDescription?.let { labelResolver?.invoke(it) }
                    if (launchPackage != null && launchPackage != stepPackage) {
                        if (!ensureForeground(launchPackage)) {
                            onComplete?.invoke(false, "Step $index: could not launch $launchPackage")
                            return
                        }
                        onStepResult?.invoke(
                            ReplayStepResult(index, true, "Step $index: launched $launchPackage")
                        )
                        lastPackage = launchPackage
                        return@forEachIndexed
                    }
                }

                if (stepPackage != null && stepPackage != lastPackage) {
                    if (!ensureForeground(stepPackage)) {
                        onComplete?.invoke(
                            false,
                            "Step $index: could not open $stepPackage"
                        )
                        return
                    }
                    lastPackage = stepPackage
                }

                val result = executeStep(
                    index = index,
                    step = step,
                    packageName = stepPackage,
                    nextStep = nextStep,
                    prevStep = plan.steps.getOrNull(stepIndex - 1)?.step,
                    precedingSearchStep = SearchStepContext.findPrecedingSearchStep(
                        plan.steps.map { it.step },
                        stepIndex
                    )
                )
                onStepResult?.invoke(result)
                if (!result.success) {
                    onComplete?.invoke(false, result.message)
                    return
                }
            }
            onComplete?.invoke(true, "Replay completed (${plan.steps.size} steps)")
        } finally {
            isReplaying = false
        }
    }

    private suspend fun ensureForeground(packageName: String): Boolean {
        if (StepPackageResolver.isSystemOverlayPackage(packageName)) {
            return waitForAppWindow(packageName)
        }
        val navigated = navigator?.invoke(packageName) ?: true
        if (!navigated && !StepPackageResolver.isLauncherPackage(packageName)) return false
        return waitForAppWindow(packageName)
    }

    private suspend fun waitForAppWindow(packageName: String): Boolean {
        repeat(APP_LAUNCH_MAX_ATTEMPTS) {
            val roots = rootsProvider(packageName)
            if (roots.isNotEmpty()) {
                roots.forEach { it.recycle() }
                delay(POST_LAUNCH_SETTLE_MS)
                return true
            }
            delay(APP_LAUNCH_RETRY_MS)
        }
        return false
    }

    private suspend fun executeStep(
        index: Int,
        step: ExportStep,
        packageName: String?,
        nextStep: ExportStep? = null,
        prevStep: ExportStep? = null,
        precedingSearchStep: ExportStep? = null
    ): ReplayStepResult {
        val action = step.action.toAction()
        val selector = ResourceIdQualifier.normalizeSelector(
            action.selector ?: return ReplayStepResult(index, false, "Step $index: no selector"),
            packageName
        )

        Log.d(TAG, "Action = ${action.type}")
        Log.d(TAG, "Selector = $selector")
        if (action.type == ActionType.SET_TEXT) {
            Log.d(
                TAG,
                "Set text value=${action.value} codePoints=${TextNormalizer.codePoints(action.value.orEmpty())}"
            )
        }

        if (action.type == ActionType.SET_TEXT && SearchScreenOpener.isSearchInputSelector(selector)) {
            ensureSearchScreenOpen(packageName, selector)
        }

        if (
            action.type == ActionType.SET_TEXT &&
            (ComposeInputHelper.isTitleOrSubjectSelector(selector) || ComposeInputHelper.isComposeBodySelector(selector))
        ) {
            primeTextField(packageName, selector)
        }

        if (
            action.type == ActionType.SET_TEXT &&
            prevStep?.action?.type == "click" &&
            ComposeInputHelper.isComposeBodySelector(selector)
        ) {
            delay(COMPOSE_BODY_SETTLE_MS)
        }

        if (
            (action.type == ActionType.CLICK || action.type == ActionType.LONG_CLICK) &&
            SearchScreenOpener.isLocationFieldSelector(selector)
        ) {
            keyboardDismisser?.invoke()
            delay(KEYBOARD_DISMISS_MS)
            ensureSearchScreenOpen(packageName, selector)
        }

        if (
            (action.type == ActionType.CLICK || action.type == ActionType.LONG_CLICK) &&
            precedingSearchStep != null
        ) {
            keyboardDismisser?.invoke()
            delay(KEYBOARD_DISMISS_MS)
            if (packageName != null) {
                ensureForeground(packageName)
            }
        }

        val maxWaitMs = when {
            action.type == ActionType.SCROLL -> SCROLL_NODE_WAIT_MS
            action.type == ActionType.SET_TEXT && ComposeInputHelper.isComposeBodySelector(selector) ->
                COMPOSE_BODY_NODE_WAIT_MS
            action.type == ActionType.SET_TEXT -> SET_TEXT_NODE_WAIT_MS
            else -> NODE_WAIT_MS
        }

        repeat(MAX_RETRIES) { attempt ->
            val waitResult = waitForNode(packageName, selector, action.type, maxWaitMs)
            if (!waitResult.hadWindow) {
                Log.d(TAG, "No active window for package=$packageName (attempt ${attempt + 1})")
                if (packageName != null) {
                    ensureForeground(packageName)
                }
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS)
                    return@repeat
                }
                if (
                    (action.type == ActionType.CLICK || action.type == ActionType.LONG_CLICK) &&
                    tryClickByResultTitle(packageName, selector)
                ) {
                    return ReplayStepResult(index, true, "Step $index: OK (result title)")
                }
                return ReplayStepResult(index, false, "Step $index: no active window")
            }
            val root = waitResult.root
            val node = waitResult.node
            if (node == null) {
                root?.recycle()
                Log.d(TAG, "Found node = false")
                val searchFallback = trySearchResultClick(packageName, precedingSearchStep, selector) ||
                    tryClickByResultTitle(packageName, selector)
                if (searchFallback) {
                    if (action.type == ActionType.CLICK || action.type == ActionType.LONG_CLICK) {
                        val navigationResult = waitForNavigation(index, packageName, nextStep)
                        if (navigationResult != null) return navigationResult
                    }
                    return ReplayStepResult(index, true, "Step $index: OK (search result)")
                }
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS)
                    return@repeat
                }
                return ReplayStepResult(
                    index,
                    false,
                    "Step $index: node not found (${selectorSummary(selector)}${screenHint(selector)})"
                )
            }

            Log.d(TAG, "Found node = true")
            Log.d(TAG, "Node class = ${node.className}")
            Log.d(TAG, "Node clickable = ${node.isClickable}")
            Log.d(TAG, "Node editable = ${node.isEditable}")

            val success = when (action.type) {
                ActionType.CLICK -> performClick(node) || trySearchResultClick(packageName, precedingSearchStep, selector) ||
                    tryClickByResultTitle(packageName, selector)
                ActionType.LONG_CLICK -> performLongClick(node)
                ActionType.SET_TEXT -> performSetText(node, TextNormalizer.normalize(action.value).orEmpty())
                ActionType.SCROLL -> performScroll(node, action.value)
            }

            var message = "Step $index: OK"
            if (success && action.type == ActionType.SET_TEXT && MessageSender.shouldAutoSendAfterMessage(step, nextStep)) {
                delay(SEND_BUTTON_APPEAR_MS)
                val sent = MessageSender.tryImeSend(node) ||
                    tryAutoSendFromRoot(packageName)
                Log.d(TAG, "Auto-send after message: sent=$sent")
                message = if (sent) "Step $index: OK (message sent)" else "Step $index: OK (send not found — tap Send)"
            }

            node.recycle()
            root?.recycle()

            if (success) {
                if (action.type == ActionType.CLICK || action.type == ActionType.LONG_CLICK) {
                    val navigationResult = waitForNavigation(index, packageName, nextStep)
                    if (navigationResult != null) return navigationResult
                } else if (action.type == ActionType.SET_TEXT || action.type == ActionType.SCROLL) {
                    val settleMs = if (
                        action.type == ActionType.SET_TEXT &&
                        SearchScreenOpener.isSearchInputSelector(selector)
                    ) {
                        POST_SEARCH_SETTLE_MS
                    } else {
                        POST_ACTION_DELAY_MS
                    }
                    delay(settleMs)
                }
                return ReplayStepResult(index, true, message)
            }
            Log.d(TAG, "Action failed on attempt ${attempt + 1}")
            if (attempt < MAX_RETRIES - 1) {
                delay(RETRY_DELAY_MS)
            }
        }

        return ReplayStepResult(index, false, "Step $index: action failed")
    }

    private suspend fun waitForNavigation(
        index: Int,
        packageName: String?,
        nextStep: ExportStep?
    ): ReplayStepResult? {
        val nextAction = nextStep?.action?.toAction() ?: run {
            delay(POST_ACTION_DELAY_MS)
            return null
        }
        val nextSelector = nextAction.selector ?: run {
            delay(POST_ACTION_DELAY_MS)
            return null
        }

        if (nextAction.type == ActionType.SET_TEXT) {
            Log.d(TAG, "Navigation wait skipped — next step types into an input field")
            delay(POST_ACTION_DELAY_MS)
            return null
        }

        if (shouldSkipNavigationWait(nextAction, nextSelector)) {
            Log.d(TAG, "Navigation wait skipped — next selector matches content not yet on screen")
            delay(POST_ACTION_DELAY_MS)
            return null
        }

        val waitResult = waitForNode(
            packageName = packageName,
            selector = nextSelector,
            actionType = nextAction.type,
            maxWaitMs = NAVIGATION_SETTLE_MS
        )
        waitResult.node?.recycle()
        waitResult.root?.recycle()

        if (!waitResult.hadWindow || waitResult.node == null) {
            Log.d(
                TAG,
                "Navigation wait: next step not visible yet (${selectorSummary(nextSelector)}${screenHint(nextSelector)}) — continuing"
            )
        }
        delay(POST_ACTION_DELAY_MS)
        return null
    }

    private fun shouldSkipNavigationWait(
        nextAction: com.pocketautomator.model.Action,
        nextSelector: com.pocketautomator.model.Selector
    ): Boolean {
        val selectorLabel = nextSelector.text ?: nextSelector.contentDescription ?: return false
        if (nextAction.type == ActionType.CLICK && selectorLabel.length > 20) {
            return true
        }
        return false
    }

    private suspend fun waitForNode(
        packageName: String?,
        selector: com.pocketautomator.model.Selector,
        actionType: ActionType,
        maxWaitMs: Long
    ): NodeWaitResult {
        val deadline = System.currentTimeMillis() + maxWaitMs
        var hadWindow = false
        while (System.currentTimeMillis() < deadline) {
            val roots = rootsProvider(packageName)
            if (roots.isNotEmpty()) {
                hadWindow = true
            }
            for (root in roots) {
                val node = nodeFinder.find(root, selector, actionType)
                if (node != null) {
                    roots.filter { it !== root }.forEach { it.recycle() }
                    return NodeWaitResult(root, node, hadWindow = true)
                }
                root.recycle()
            }
            delay(NODE_POLL_MS)
        }
        return NodeWaitResult(root = null, node = null, hadWindow = hadWindow)
    }

    private suspend fun primeTextField(
        packageName: String?,
        selector: com.pocketautomator.model.Selector
    ) {
        val hints = ComposeInputHelper.placeholderHintsFor(selector)
        if (hints.isEmpty()) return

        for (root in rootsProvider(packageName)) {
            var primed = false
            for (hint in hints) {
                val nodes = root.findAccessibilityNodeInfosByText(hint)
                for (node in nodes) {
                    val clickTarget = findClickableAncestor(node) ?: node
                    if (performClick(clickTarget)) {
                        primed = true
                    }
                    if (clickTarget !== node) clickTarget.recycle()
                    node.recycle()
                    if (primed) break
                }
                if (primed) break
            }
            root.recycle()
            if (primed) {
                Log.d(TAG, "Primed text field using placeholder hints=$hints")
                delay(PRIME_INPUT_MS)
                return
            }
        }
    }

    private suspend fun ensureSearchScreenOpen(
        packageName: String?,
        inputSelector: com.pocketautomator.model.Selector
    ): Boolean {
        val quickCheck = waitForNode(packageName, inputSelector, ActionType.SET_TEXT, SEARCH_INPUT_PROBE_MS)
        if (quickCheck.node != null) {
            quickCheck.node.recycle()
            quickCheck.root?.recycle()
            return true
        }
        quickCheck.root?.recycle()

        Log.d(TAG, "Opening search screen for package=$packageName")
        repeat(SEARCH_OPEN_ATTEMPTS) {
            val roots = rootsProvider(packageName)
            if (roots.isEmpty()) {
                delay(NODE_POLL_MS)
                return@repeat
            }
            var opened = false
            for (root in roots) {
                opened = SearchScreenOpener.tryOpen(root, packageName, nodeFinder, ::performClick)
                root.recycle()
                if (opened) break
            }
            if (!opened) {
                delay(NODE_POLL_MS)
                return@repeat
            }
            delay(SEARCH_SCREEN_SETTLE_MS)
            val retry = waitForNode(packageName, inputSelector, ActionType.SET_TEXT, SEARCH_INPUT_PROBE_MS)
            val found = retry.node != null
            retry.node?.recycle()
            retry.root?.recycle()
            if (found) {
                Log.d(TAG, "Search screen opened")
                return true
            }
        }
        Log.d(TAG, "Could not open search screen")
        return false
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        if (node.performAction(AccessibilityNodeInfo.ACTION_SELECT)) return true
        if (node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            if (node.performAction(AccessibilityNodeInfo.ACTION_SELECT)) return true
        }
        return findClickableAncestor(node)?.let { ancestor ->
            val result = ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                ancestor.performAction(AccessibilityNodeInfo.ACTION_SELECT)
            ancestor.recycle()
            result
        } ?: false
    }

    private fun performLongClick(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true
        return findClickableAncestor(node)?.let { ancestor ->
            val result = ancestor.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            ancestor.recycle()
            result
        } ?: false
    }

    private fun performSetText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        var target = findEditableNode(node) ?: node
        var shouldRecycleTarget = target !== node

        if (!isEditableInput(target)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            findEditableNode(node)?.let { refreshed ->
                if (shouldRecycleTarget) target.recycle()
                target = refreshed
                shouldRecycleTarget = true
            }
        }

        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            if (shouldRecycleTarget) target.recycle()
            return true
        }
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                if (shouldRecycleTarget) target.recycle()
                return true
            }
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            if (shouldRecycleTarget) target.recycle()
            return true
        }
        if (shouldRecycleTarget) target.recycle()
        return false
    }

    private fun tryAutoSendFromRoot(packageName: String?): Boolean {
        for (root in rootsProvider(packageName)) {
            val sent = MessageSender.trySend(root, nodeFinder, ::performClick)
            root.recycle()
            if (sent) return true
        }
        return false
    }

    private fun trySearchResultClick(
        packageName: String?,
        searchStep: ExportStep?,
        clickSelector: com.pocketautomator.model.Selector
    ): Boolean {
        if (searchStep?.action?.type != "set_text") return false
        val query = searchStep.action.value ?: return false
        if (!SearchStepContext.isSearchInputStep(searchStep)) {
            return false
        }
        if (!SearchResultClicker.isPostSearchResultClick(clickSelector, query)) {
            return false
        }
        val resultLabel = when {
            SearchResultClicker.isMisrecordedEntryClickAfterSearch(clickSelector, query) -> query
            else -> SearchResultClicker.selectorResultLabel(clickSelector) ?: query
        }
        for (root in rootsProvider(packageName)) {
            Log.d(TAG, "Fallback: clicking search result for query=$query label=$resultLabel")
            val clicked = SearchResultClicker.clickResult(root, clickSelector, query, ::performClick)
            root.recycle()
            if (clicked) return true
        }
        return false
    }

    private fun tryClickByResultTitle(
        packageName: String?,
        clickSelector: com.pocketautomator.model.Selector
    ): Boolean {
        val label = SearchResultClicker.selectorResultLabel(clickSelector) ?: return false
        val id = clickSelector.resourceId?.substringAfterLast('/').orEmpty().lowercase()
        val looksLikeListRow = id.contains("row") || id.contains("result") || id.contains("item") ||
            clickSelector.path != null
        if (!looksLikeListRow) return false
        for (root in rootsProvider(packageName)) {
            Log.d(TAG, "Fallback: clicking result by title=$label")
            val clicked = SearchResultClicker.clickResult(root, label, ::performClick, label)
            root.recycle()
            if (clicked) return true
        }
        return false
    }

    private fun performScroll(node: AccessibilityNodeInfo, direction: String?): Boolean {
        val target = findScrollableTarget(node) ?: node
        val shouldRecycle = target !== node
        val scrollAction = when (direction) {
            "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val scrolled = target.performAction(scrollAction)
        if (shouldRecycle) target.recycle()
        return scrolled
    }

    private fun findScrollableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
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
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isEditableInput(node)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val editable = findEditableNode(child)
            child.recycle()
            if (editable != null) return editable
        }
        return null
    }

    private fun isEditableInput(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        val className = node.className?.toString().orEmpty()
        if (className.contains("WebView", ignoreCase = true) && node.isFocusable) return true
        return className.contains("EditText", ignoreCase = true) ||
            className.contains("AutoComplete", ignoreCase = true)
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent
        while (current != null) {
            if (current.isClickable) {
                return AccessibilityNodeInfo.obtain(current)
            }
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return null
    }

    private fun selectorSummary(selector: com.pocketautomator.model.Selector): String {
        return listOfNotNull(
            selector.contentDescription?.let { "desc=$it" },
            selector.text?.let { "text=$it" },
            selector.className?.let { "class=$it" },
            selector.resourceId?.let { "id=$it" },
            selector.path?.let { "path=$it" }
        ).joinToString(", ")
    }

    private fun screenHint(selector: com.pocketautomator.model.Selector): String {
        val id = selector.resourceId?.lowercase().orEmpty()
        return when {
            "updates_list" in id || "updates" in id -> " — Updates tab may not be open"
            "conversations" in id || "chat" in id -> " — Chats tab may not be open"
            "browse" in id -> " — Browse tab may not be open"
            "search" in id -> " — Search screen may not be open"
            "location" in id || "destination" in id -> " — destination field may not be visible (open ride screen)"
            "library" in id -> " — Library tab may not be open"
            "recycler" in id || id.endsWith("_list") -> " — list may not have loaded"
            else -> ""
        }
    }

    companion object {
        private const val TAG = "REPLAY"
        private const val STEP_DELAY_MS = 500L
        private const val RETRY_DELAY_MS = 600L
        private const val MAX_RETRIES = 3
        private const val APP_LAUNCH_RETRY_MS = 500L
        private const val APP_LAUNCH_MAX_ATTEMPTS = 30
        private const val POST_LAUNCH_SETTLE_MS = 800L
        private const val NODE_POLL_MS = 400L
        private const val NODE_WAIT_MS = 4_000L
        private const val SCROLL_NODE_WAIT_MS = 10_000L
        private const val SET_TEXT_NODE_WAIT_MS = 8_000L
        private const val COMPOSE_BODY_NODE_WAIT_MS = 12_000L
        private const val COMPOSE_BODY_SETTLE_MS = 1_500L
        private const val PRIME_INPUT_MS = 600L
        private const val POST_ACTION_DELAY_MS = 700L
        private const val POST_SEARCH_SETTLE_MS = 1_500L
        private const val NAVIGATION_SETTLE_MS = 12_000L
        private const val SEARCH_INPUT_PROBE_MS = 1_200L
        private const val SEARCH_SCREEN_SETTLE_MS = 1_000L
        private const val SEARCH_OPEN_ATTEMPTS = 3
        private const val SEND_BUTTON_APPEAR_MS = 400L
        private const val KEYBOARD_DISMISS_MS = 300L
    }
}
