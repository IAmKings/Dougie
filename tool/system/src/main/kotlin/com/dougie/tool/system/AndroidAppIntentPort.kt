package com.dougie.tool.system

import android.content.Intent
import android.net.Uri
import android.content.Context
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AppIntentPort

class AndroidAppIntentPort(
    context: Context,
    private val isForeground: () -> Boolean,
) : AppIntentPort {
    private val appContext = context.applicationContext

    override fun isAppForeground(): Boolean = isForeground()

    override fun launchView(uri: String, packageName: String?): String {
        return try {
            val intent = intentFor(uri, packageName) ?: return failJson()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            okJson(uri)
        } catch (_: Exception) {
            failJson()
        }
    }

    private fun intentFor(uri: String, packageName: String?): Intent? {
        if (uri.startsWith("package:")) {
            val pkg = uri.removePrefix("package:")
            return appContext.packageManager.getLaunchIntentForPackage(pkg)
        }
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (!packageName.isNullOrBlank()) {
            view.setPackage(packageName)
        }
        return view
    }

    private fun okJson(uri: String): String {
        val escaped = uri.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"ok":true,"launched":"$escaped"}"""
    }

    private fun failJson(): String =
        """{"ok":false,"error":"${UserFacingErrors.APP_INTENT_LAUNCH_FAILED}"}"""
}
