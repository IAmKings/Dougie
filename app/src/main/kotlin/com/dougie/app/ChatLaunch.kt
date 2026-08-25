package com.dougie.app

import android.content.Context
import android.content.Intent

object ChatLaunch {
    const val EXTRA_OPEN_CHAT = "com.dougie.app.extra.OPEN_CHAT"

    val activityFlags: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP

    fun requestsChat(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true
}

fun chatLaunchIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = ChatLaunch.activityFlags
        putExtra(ChatLaunch.EXTRA_OPEN_CHAT, true)
    }
