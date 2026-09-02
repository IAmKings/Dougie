package com.dougie.app

import android.content.Context
import android.content.Intent

object ChatLaunch {
    const val EXTRA_OPEN_CHAT = "com.dougie.app.extra.OPEN_CHAT"
    const val EXTRA_SCHEDULE_ID = "com.dougie.app.extra.SCHEDULE_ID"
    const val EXTRA_APPLY_PINNED_SCREEN = "com.dougie.app.extra.APPLY_PINNED_SCREEN"
    const val EXTRA_OPEN_PERMISSIONS = "com.dougie.app.extra.OPEN_PERMISSIONS"

    val activityFlags: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP

    fun requestsChat(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true ||
            scheduleId(intent) != null ||
            applyPinnedScreen(intent) ||
            openPermissions(intent)

    fun scheduleId(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_SCHEDULE_ID)?.takeIf { it.isNotBlank() }

    fun applyPinnedScreen(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_APPLY_PINNED_SCREEN, false) == true

    fun openPermissions(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_OPEN_PERMISSIONS, false) == true
}

fun chatLaunchIntent(
    context: Context,
    scheduleId: String? = null,
    applyPinnedScreen: Boolean = false,
    openPermissions: Boolean = false,
): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = ChatLaunch.activityFlags
        putExtra(ChatLaunch.EXTRA_OPEN_CHAT, true)
        if (!scheduleId.isNullOrBlank()) {
            putExtra(ChatLaunch.EXTRA_SCHEDULE_ID, scheduleId)
        }
        if (applyPinnedScreen) {
            putExtra(ChatLaunch.EXTRA_APPLY_PINNED_SCREEN, true)
        }
        if (openPermissions) {
            putExtra(ChatLaunch.EXTRA_OPEN_PERMISSIONS, true)
        }
    }
