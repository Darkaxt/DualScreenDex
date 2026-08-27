package com.darkaxt.dualdex.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

enum class AllFilesSettingsDestination {
    PACKAGE_SETTINGS,
    GLOBAL_SETTINGS,
    SAF_FALLBACK,
}

internal class AllFilesSettingsLaunchCoordinator(
    private val openPackageSettings: () -> Boolean,
    private val openGlobalSettings: () -> Boolean,
    private val openSafFallback: () -> Unit,
) {
    fun open(): AllFilesSettingsDestination {
        if (attempt(openPackageSettings)) return AllFilesSettingsDestination.PACKAGE_SETTINGS
        if (attempt(openGlobalSettings)) return AllFilesSettingsDestination.GLOBAL_SETTINGS
        runCatching(openSafFallback)
        return AllFilesSettingsDestination.SAF_FALLBACK
    }

    private fun attempt(action: () -> Boolean): Boolean = runCatching(action).getOrDefault(false)
}

object AllFilesSettingsLauncher {
    fun open(context: Context, openSafFallback: () -> Unit): AllFilesSettingsDestination =
        AllFilesSettingsLaunchCoordinator(
            openPackageSettings = {
                context.openIfResolvable(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
            openGlobalSettings = {
                context.openIfResolvable(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            },
            openSafFallback = openSafFallback,
        ).open()

    private fun Context.openIfResolvable(intent: Intent): Boolean {
        if (intent.resolveActivity(packageManager) == null) return false
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }
}
