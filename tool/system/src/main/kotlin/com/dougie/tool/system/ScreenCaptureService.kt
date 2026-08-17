package com.dougie.tool.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.graphics.PixelFormat
import androidx.core.app.NotificationCompat
import com.dougie.core.tool.ScreenFrame
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScreenCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startProjectionForeground()
        } catch (e: Exception) {
            ScreenCaptureBridge.pending?.completeExceptionally(e)
            stopSelf()
            return START_NOT_STICKY
        }
        Thread({
            try {
                val frame = captureOneFrame()
                ScreenCaptureBridge.pending?.complete(frame)
            } catch (e: Exception) {
                ScreenCaptureBridge.pending?.completeExceptionally(e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "dougie-capture-run").start()
        return START_NOT_STICKY
    }

    private fun startProjectionForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "屏幕截取", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dougie")
            .setContentText("正在截取屏幕")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun captureOneFrame(): ScreenFrame {
        val token = ScreenCaptureConsentStore.data
            ?: error("missing projection token")
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(ScreenCaptureConsentStore.resultCode, token)
            ?: error("media projection unavailable")
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val dpi = metrics.densityDpi
        val thread = HandlerThread("dougie-screen-capture").apply { start() }
        val handler = Handler(thread.looper)
        var reader: ImageReader? = null
        var display: VirtualDisplay? = null
        val latch = CountDownLatch(1)
        var captured: ScreenFrame? = null
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                latch.countDown()
            }
        }
        try {
            projection.registerCallback(callback, handler)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader.setOnImageAvailableListener(
                { imageReader ->
                    imageReader.acquireLatestImage()?.use { image ->
                        captured = toGrayFrame(image)
                    }
                    latch.countDown()
                },
                handler,
            )
            display = projection.createVirtualDisplay(
                "dougie-capture",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
            latch.await(5, TimeUnit.SECONDS)
            return captured ?: error("no frame")
        } finally {
            display?.release()
            reader?.close()
            runCatching { projection.unregisterCallback(callback) }
            runCatching { projection.stop() }
            ScreenCaptureConsentStore.clear()
            thread.quitSafely()
        }
    }

    private fun toGrayFrame(image: Image): ScreenFrame {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val gray = ByteArray(width * height)
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            val toRead = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, toRead)
            for (x in 0 until width) {
                val i = x * pixelStride
                val r = row[i].toInt() and 0xFF
                val g = row[i + 1].toInt() and 0xFF
                val b = row[i + 2].toInt() and 0xFF
                gray[y * width + x] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            }
        }
        return ScreenFrame(
            id = UUID.randomUUID().toString(),
            width = width,
            height = height,
            gray = gray,
        )
    }

    companion object {
        private const val CHANNEL_ID = "dougie_screen_capture"
        private const val NOTIFICATION_ID = 47
    }
}
