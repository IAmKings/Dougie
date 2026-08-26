package com.dougie.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

object ScheduleAlarms {
    const val ACTION_FIRE = "com.dougie.app.action.SCHEDULE_FIRE"
    const val EXTRA_ID = "com.dougie.app.extra.SCHEDULE_ALARM_ID"

    fun sync(context: Context) {
        val app = context.applicationContext
        val store = ScheduleStore(app.filesDir)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        store.list().forEach { item ->
            set(app, item, nextTriggerEpochMs(item, now, zone))
        }
    }

    fun set(context: Context, item: ScheduleItem, epochMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, item.id)
        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi)
        }
    }

    fun cancel(context: Context, id: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, id))
    }

    fun exactLikely(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, DougieScheduleReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, id)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }
}
