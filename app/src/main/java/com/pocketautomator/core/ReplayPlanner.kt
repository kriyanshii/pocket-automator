package com.pocketautomator.core

import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportRecording
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.Selector

object ReplayPlanner {

    data class PlannedStep(val originalIndex: Int, val step: ExportStep)

    data class ReplayPlan(
        val steps: List<PlannedStep>,
        val initialLaunchPackage: String? = null,
        val skippedLauncherSteps: Int = 0
    )

    fun plan(recording: ExportRecording): ReplayPlan {
        val targetApp = recording.app.takeIf { it.isNotBlank() }
        val allSteps = recording.steps.mapIndexed { index, step -> PlannedStep(index, step) }
        val basePlan = when {
            targetApp == null -> ReplayPlan(allSteps)
            else -> planWithLauncherSkip(recording, allSteps, targetApp)
        }
        return basePlan.copy(
            steps = filterSpuriousScrolls(
                rewritePostSearchClicks(
                    coalesceSetTextSteps(
                        filterPlaceholderSetTextSteps(
                            sanitizeSetTextSelectors(sanitizeClickSelectors(basePlan.steps))
                        )
                    )
                )
            )
        )
    }

    private fun planWithLauncherSkip(
        recording: ExportRecording,
        allSteps: List<PlannedStep>,
        targetApp: String
    ): ReplayPlan {
        val firstTargetIndex = recording.steps.indexOfFirst { step ->
            val pkg = StepPackageResolver.resolve(step)
            pkg != null && !StepPackageResolver.isLauncherPackage(pkg)
        }
        if (firstTargetIndex <= 0) {
            val processed = filterSpuriousScrolls(
                rewritePostSearchClicks(
                    coalesceSetTextSteps(
                        filterPlaceholderSetTextSteps(
                            sanitizeSetTextSelectors(sanitizeClickSelectors(allSteps))
                        )
                    )
                )
            )
            val launchPackage = targetApp
                ?: StepPackageResolver.resolve(processed.firstOrNull()?.step ?: return ReplayPlan(processed))
            return ReplayPlan(
                steps = processed,
                initialLaunchPackage = launchPackage?.takeUnless { StepPackageResolver.isLauncherPackage(it) }
            )
        }

        val launcherPrefix = recording.steps.subList(0, firstTargetIndex).all { step ->
            val pkg = StepPackageResolver.resolve(step)
            pkg == null || StepPackageResolver.isLauncherPackage(pkg)
        }
        if (!launcherPrefix) return ReplayPlan(allSteps)

        val remaining = allSteps.drop(firstTargetIndex)
        val launchPackage = StepPackageResolver.resolve(remaining.first().step) ?: targetApp
        return ReplayPlan(
            steps = remaining,
            initialLaunchPackage = launchPackage,
            skippedLauncherSteps = firstTargetIndex
        )
    }

    fun filterSpuriousScrolls(steps: List<PlannedStep>): List<PlannedStep> {
        if (steps.isEmpty()) return steps
        val result = mutableListOf<PlannedStep>()
        for (planned in steps) {
            val step = planned.step
            if (step.action.type == "scroll") {
                val prev = result.lastOrNull()?.step
                if (prev?.action?.type == "set_text") continue
                if (
                    prev?.action?.type == "scroll" &&
                    prev.action.resourceId == step.action.resourceId &&
                    prev.action.direction == step.action.direction
                ) {
                    continue
                }
            }
            result.add(planned)
        }
        return result
    }

    fun sanitizeClickSelectors(steps: List<PlannedStep>): List<PlannedStep> {
        return steps.map { planned ->
            val action = planned.step.action
            if (action.type != "click" && action.type != "long_click") return@map planned
            val text = action.text
            if (text.isNullOrBlank() || action.resourceId.isNullOrBlank()) return@map planned
            val suffix = action.resourceId.substringAfterLast('/').lowercase()
            val dropText = (suffix == "entry" || suffix.contains("input")) &&
                (text.equals("Message", ignoreCase = true) ||
                    text.equals("Search", ignoreCase = true) ||
                    text.startsWith("Type a", ignoreCase = true))
            if (!dropText) return@map planned
            planned.copy(
                step = planned.step.copy(
                    action = action.copy(text = null, path = null)
                )
            )
        }
    }

    fun filterPlaceholderSetTextSteps(steps: List<PlannedStep>): List<PlannedStep> {
        return steps.filter { planned ->
            val action = planned.step.action
            if (action.type != "set_text") return@filter true
            !PlaceholderText.isPlaceholderValue(action.value)
        }
    }

    fun rewritePostSearchClicks(steps: List<PlannedStep>): List<PlannedStep> {
        val exportSteps = steps.map { it.step }
        return steps.mapIndexed { index, planned ->
            val searchStep = SearchStepContext.findPrecedingSearchStep(exportSteps, index)
                ?: return@mapIndexed planned
            val action = planned.step.action
            if (action.type != "click" && action.type != "long_click") return@mapIndexed planned
            val query = searchStep.action.value ?: return@mapIndexed planned
            if (!SearchResultClicker.isPostSearchResultClick(
                    Selector(
                        resourceId = action.resourceId,
                        contentDescription = action.contentDescription,
                        text = action.text,
                        className = action.className,
                        path = action.path
                    ),
                    query
                )
            ) {
                return@mapIndexed planned
            }
            val resultLabel = when {
                SearchResultClicker.isMisrecordedEntryClickAfterSearch(
                    Selector(resourceId = action.resourceId, text = action.text, className = action.className),
                    query
                ) -> query
                else -> action.text?.takeIf { it.isNotBlank() }
                    ?: action.contentDescription?.takeIf { it.isNotBlank() }
                    ?: query
            }
            val isWhatsApp = planned.step.packageName.contains("whatsapp", ignoreCase = true)
            planned.copy(
                step = planned.step.copy(
                    action = if (isWhatsApp) {
                        action.copy(
                            type = "click",
                            resourceId = "com.whatsapp:id/contact_row_container",
                            text = resultLabel,
                            className = "android.widget.RelativeLayout",
                            path = null
                        )
                    } else {
                        action.copy(
                            type = "click",
                            resourceId = null,
                            text = resultLabel,
                            className = null,
                            path = null
                        )
                    }
                )
            )
        }
    }

    fun sanitizeSetTextSelectors(steps: List<PlannedStep>): List<PlannedStep> {
        return steps.map { planned ->
            val action = planned.step.action
            if (action.type != "set_text") return@map planned
            planned.copy(
                step = planned.step.copy(
                    action = action.copy(
                        text = null,
                        path = action.path?.takeIf { action.resourceId.isNullOrBlank() },
                        resourceId = action.resourceId ?: inferMessageEntryResourceId(planned.step, action)
                    )
                )
            )
        }
    }

    private fun inferMessageEntryResourceId(step: ExportStep, action: ExportAction): String? {
        if (!step.packageName.contains("whatsapp", ignoreCase = true)) return null
        if (action.className?.contains("EditText", ignoreCase = true) == true) {
            return "com.whatsapp:id/entry"
        }
        return null
    }

    fun coalesceSetTextSteps(steps: List<PlannedStep>): List<PlannedStep> {
        if (steps.isEmpty()) return steps
        val result = mutableListOf<PlannedStep>()
        for (planned in steps) {
            val step = planned.step
            if (step.action.type == "set_text") {
                val prev = result.lastOrNull()
                if (prev?.step?.action?.type == "set_text") {
                    val sameResourceId = prev.step.action.resourceId != null &&
                        prev.step.action.resourceId == step.action.resourceId
                    val samePath = prev.step.action.resourceId.isNullOrBlank() &&
                        prev.step.action.path != null &&
                        prev.step.action.path == step.action.path
                    if (sameResourceId || samePath) {
                        result[result.lastIndex] = planned.copy(step = step)
                        continue
                    }
                }
            }
            result.add(planned)
        }
        return result
    }

    fun isLauncherAppLaunch(step: ExportStep, stepPackage: String?): Boolean {
        if (stepPackage == null || !StepPackageResolver.isLauncherPackage(stepPackage)) return false
        if (step.action.type != "click" && step.action.type != "long_click") return false
        return !step.action.text.isNullOrBlank() ||
            !step.action.contentDescription.isNullOrBlank() ||
            step.action.resourceId?.endsWith(":id/icon") == true
    }
}
