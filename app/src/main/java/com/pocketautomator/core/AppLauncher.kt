package com.pocketautomator.core

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object AppLauncher {

    fun launch(context: Context, packageName: String): Boolean {
        val launchIntent = resolveLaunchIntent(context.packageManager, packageName) ?: return false
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        if (launchWithPendingIntent(context, launchIntent)) return true
        return launchWithStartActivity(context, launchIntent)
    }

    private fun resolveLaunchIntent(packageManager: PackageManager, packageName: String): Intent? {
        packageManager.getLaunchIntentForPackage(packageName)?.let { return it }

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val activities = packageManager.queryIntentActivities(mainIntent, 0)
        if (activities.isEmpty()) return null

        val activity = activities.first().activityInfo
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(activity.packageName, activity.name)
        }
    }

    private fun launchWithPendingIntent(context: Context, launchIntent: Intent): Boolean {
        return try {
            val pendingIntent = PendingIntent.getActivity(
                context,
                launchIntent.component?.hashCode() ?: launchIntent.hashCode(),
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                pendingIntent.send(
                    context,
                    0,
                    null,
                    null,
                    null,
                    null,
                    options.toBundle()
                )
            } else {
                pendingIntent.send()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchWithStartActivity(context: Context, launchIntent: Intent): Boolean {
        return try {
            context.applicationContext.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
