package com.dougie.app

import android.content.Context
import android.content.Intent

object ChatLaunch {
    const val EXTRA_OPEN_CHAT = "com.dougie.app.extra.OPEN_CHAT"
    const val EXTRA_SCHEDULE_ID = "com.dougie.app.extra.SCHEDULE_ID"

    val activityFlags: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP

    fun requestsChat(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true ||
            scheduleId(intent) != null

    fun scheduleId(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_SCHEDULE_ID)?.takeIf { it.isNotBlank() }
}

fun chatLaunchIntent(context: Context, scheduleId: String? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = ChatLaunch.activityFlags
        putExtra(ChatLaunch.EXTRA_OPEN_CHAT, true)
        if (!scheduleId.isNullOrBlank()) {
            putExtra(ChatLaunch.EXTRA_SCHEDULE_ID, scheduleId)
        }
    }
