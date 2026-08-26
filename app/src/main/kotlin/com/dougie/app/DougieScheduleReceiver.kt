package com.dougie.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dougie.core.model.AndroidPermissions
import java.time.ZoneId

class DougieScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        if (intent?.action != ScheduleAlarms.ACTION_FIRE) return
        val id = intent.getStringExtra(ScheduleAlarms.EXTRA_ID) ?: return
        val store = ScheduleStore(app.filesDir)
        val item = store.find(id) ?: return
        store.putPendingDraft(id, item.draft)
        postNotice(app, item)
        val next = afterScheduleFire(store.list(), id)
        store.save(next)
        ScheduleAlarms.cancel(app, id)
        next.find { it.id == id }?.let { still ->
            ScheduleAlarms.set(
                app,
                still,
                nextTriggerEpochMs(still, System.currentTimeMillis(), ZoneId.systemDefault()),
            )
        }
    }

    private fun postNotice(context: Context, item: ScheduleItem) {
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(
                context,
                AndroidPermissions.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "定时提醒", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val launch = chatLaunchIntent(context, item.id)
        val pending = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Dougie")
            .setContentText(formatScheduleNotice(item.hour, item.minute))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(item.id, NOTIFICATION_ID, notification) }
    }

    companion object {
        const val CHANNEL_ID = "dougie_schedule"
        const val NOTIFICATION_ID = 49
    }
}
