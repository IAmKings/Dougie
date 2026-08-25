package com.dougie.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.dougie.core.model.AgentTask
import com.dougie.core.model.AndroidPermissions
import com.dougie.core.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object NotificationPermissionGate {
    private val requested = AtomicBoolean(false)

    fun tryMarkRequested(): Boolean = requested.compareAndSet(false, true)
}

class TaskProgressNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun start(scope: CoroutineScope, tasks: StateFlow<AgentTask?>) {
        ensureChannel()
        scope.launch {
            tasks.collect { apply(it) }
        }
    }

    fun apply(task: AgentTask?) {
        val line = formatTaskNotice(task)
        if (line == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canPost()) return
        val ongoing = task?.status != TaskStatus.COMPLETED && task?.status != TaskStatus.FAILED
        val launch = chatLaunchIntent(context)
        val contentPending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val bubblePending = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BUBBLE,
            launch,
            taskNoticeBubblePendingFlags(Build.VERSION.SDK_INT),
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Dougie")
            .setContentText(line)
            .setContentIntent(contentPending)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
        attachPlayBubble(builder, bubblePending)
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
            .onFailure {
                builder.setBubbleMetadata(null)
                runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
            }
    }

    private fun attachPlayBubble(builder: NotificationCompat.Builder, pending: PendingIntent) {
        if (BuildConfig.IS_SIDELOAD) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val bubble = NotificationCompat.BubbleMetadata.Builder(pending, icon)
            .setDesiredHeight(640)
            .setAutoExpandBubble(false)
            .build()
        builder.setBubbleMetadata(bubble)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            AndroidPermissions.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "任务状态", NotificationManager.IMPORTANCE_LOW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !BuildConfig.IS_SIDELOAD) {
            channel.setAllowBubbles(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "dougie_task_progress"
        const val NOTIFICATION_ID = 48
        private const val REQUEST_CODE = 48
        private const val REQUEST_CODE_BUBBLE = 49
    }
}
