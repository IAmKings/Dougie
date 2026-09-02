package com.dougie.app

import android.content.Context
import android.content.Intent
import com.dougie.feature.settings.LauncherApp

object LauncherApps {
    fun list(context: Context): List<LauncherApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(query, 0).map { info ->
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString().trim().ifEmpty { pkg }
            LauncherApp(label = label, packageName = pkg)
        }.distinctBy { it.packageName }.sortedBy { it.label }
    }
}
