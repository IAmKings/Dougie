package com.dougie.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Confirmation actions on the AWAITING_CONFIRMATION notification let the user approve or
 * reject a tool call from the notification shade while a third-party app is in the
 * foreground — switching back to Dougie to tap the in-app confirm card would change the
 * foreground app and break tap-by-text targeting. No activity trampoline: the receiver
 * completes the confirm gate in-process.
 */
class TaskConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = (context.applicationContext as? DougieApplication)?.taskManager ?: return
        when (intent.action) {
            ACTION_CONFIRM -> manager.confirm()
            ACTION_REJECT -> manager.reject()
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.dougie.app.action.CONFIRM_TASK"
        const val ACTION_REJECT = "com.dougie.app.action.REJECT_TASK"
    }
}
