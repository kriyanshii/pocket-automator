package com.pocketautomator.core

import com.pocketautomator.model.ExportAction
import com.pocketautomator.model.ExportStep
import com.pocketautomator.model.ScreenNode

object StepPackageResolver {

    fun resolve(step: ExportStep): String? {
        if (step.packageName.isNotBlank()) return step.packageName
        inferFromResourceId(step.action.resourceId)?.let { return it }
        findPackageInScreen(step.screen)?.let { return it }
        return null
    }

    fun inferFromResourceId(resourceId: String?): String? {
        if (resourceId.isNullOrBlank()) return null
        val separator = resourceId.indexOf(':')
        if (separator <= 0) return null
        return resourceId.substring(0, separator)
    }

    fun isLauncherPackage(packageName: String): Boolean {
        return packageName.contains("launcher", ignoreCase = true) ||
            packageName == SYSTEM_UI_PACKAGE
    }

    fun isSystemOverlayPackage(packageName: String): Boolean {
        return packageName == SYSTEM_UI_PACKAGE
    }

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    private fun findPackageInScreen(node: ScreenNode): String? {
        inferFromResourceId(node.resourceId)?.let { return it }
        for (child in node.children) {
            findPackageInScreen(child)?.let { return it }
        }
        return null
    }
}
