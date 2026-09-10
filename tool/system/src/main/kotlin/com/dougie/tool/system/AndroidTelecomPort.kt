package com.dougie.tool.system

import android.content.Intent
import android.content.Context
import android.net.Uri
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.TelecomPort

class AndroidTelecomPort(
    context: Context,
    private val isForeground: () -> Boolean,
) : TelecomPort {
    private val appContext = context.applicationContext

    override fun isAppForeground(): Boolean = isForeground()

    override fun composeSms(to: String, body: String): String {
        if (!isForeground()) return failForeground()
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(to)))
            intent.putExtra("sms_body", body)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(appContext.packageManager) == null) return failJson()
            appContext.startActivity(intent)
            okJson()
        } catch (_: Exception) {
            failJson()
        }
    }

    override fun dial(number: String): String {
        if (!isForeground()) return failForeground()
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(appContext.packageManager) == null) return failJson()
            appContext.startActivity(intent)
            okJson()
        } catch (_: Exception) {
            failJson()
        }
    }

    private fun okJson(): String = """{"ok":true}"""

    private fun failForeground(): String =
        """{"ok":false,"error":"${UserFacingErrors.APP_INTENT_NOT_FOREGROUND}"}"""

    private fun failJson(): String =
        """{"ok":false,"error":"${UserFacingErrors.TELECOM_LAUNCH_FAILED}"}"""
}
