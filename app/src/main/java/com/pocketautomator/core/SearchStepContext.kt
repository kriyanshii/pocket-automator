package com.pocketautomator.core

import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.Selector

object SearchStepContext {

    fun isSearchInputStep(step: ExportStep): Boolean {
        val action = step.action
        if (action.type != "set_text") return false
        val selector = Selector(
            resourceId = action.resourceId,
            className = action.className,
            path = action.path
        )
        if (SearchScreenOpener.isSearchInputSelector(selector)) return true
        if (step.packageName.contains("ubercab", ignoreCase = true) &&
            SearchScreenOpener.isLocationFieldSelector(selector)
        ) {
            return true
        }
        if (step.packageName.contains("spotify", ignoreCase = true) &&
            action.className?.contains("EditText", ignoreCase = true) == true
        ) {
            return true
        }
        return false
    }

    fun findPrecedingSearchStep(steps: List<ExportStep>, beforeIndex: Int): ExportStep? {
        for (i in beforeIndex - 1 downTo 0) {
            val step = steps.getOrNull(i) ?: continue
            if (step.action.type != "set_text") continue
            if (PlaceholderText.isPlaceholderValue(step.action.value)) continue
            if (!isSearchInputStep(step)) continue
            return step
        }
        return null
    }

    fun findPrecedingSearchQuery(steps: List<ExportStep>, beforeIndex: Int): String? =
        findPrecedingSearchStep(steps, beforeIndex)?.action?.value?.takeIf { it.isNotBlank() }
}
