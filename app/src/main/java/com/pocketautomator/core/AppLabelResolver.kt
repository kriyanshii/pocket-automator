package com.pocketautomator.core

import android.content.Intent
import android.content.pm.PackageManager

object AppLabelResolver {

    fun packageForLabel(packageManager: PackageManager, label: String): String? {
        if (label.isBlank()) return null
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val normalized = label.trim()
        return packageManager.queryIntentActivities(mainIntent, 0)
            .firstOrNull { resolveInfo ->
                resolveInfo.loadLabel(packageManager).toString()
                    .equals(normalized, ignoreCase = true)
            }
            ?.activityInfo
            ?.packageName
    }
}
