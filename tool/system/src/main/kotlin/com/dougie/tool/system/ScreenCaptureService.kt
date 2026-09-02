package com.dougie.tool.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.dougie.core.tool.CapturedScreen
import com.dougie.core.tool.ScreenFrame
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScreenCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionLock = Any()
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var projection: MediaProjection? = null
    @Volatile private var fgsStarted = false
    private var sessionReader: ImageReader? = null
    private var sessionDisplay: VirtualDisplay? = null
    private var grabLatch: CountDownLatch? = null
    private var pendingShot: CapturedScreen? = null
    private val grabLock = Any()

    private val sessionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            dropHardware(clearToken = false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            dropHardware(clearToken = true)
            return START_NOT_STICKY
        }
        try {
            startProjectionForeground()
        } catch (e: Exception) {
            if (!fgsStarted) {
                ScreenCaptureBridge.pending?.completeExceptionally(e)
                dropHardware(clearToken = false)
                return START_NOT_STICKY
            }
        }
        Thread({
            try {
                val frame = synchronized(grabLock) { takeFrame() }
                ScreenCaptureBridge.pending?.complete(frame)
            } catch (e: Exception) {
                ScreenCaptureBridge.pending?.completeExceptionally(e)
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
            .setContentText("Dougie 可以截取屏幕")
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
        fgsStarted = true
        ScreenCaptureSession.foreground = true
    }

    private fun takeFrame(): CapturedScreen {
        val handler: Handler
        synchronized(sessionLock) {
            ensureProjectionLocked()
            handler = captureHandler ?: error("no capture handler")
        }
        val metrics = resources.displayMetrics
        val fullW = metrics.widthPixels.coerceAtLeast(1)
        val fullH = metrics.heightPixels.coerceAtLeast(1)
        val scale = if (fullW > MAX_CAPTURE_WIDTH) MAX_CAPTURE_WIDTH.toFloat() / fullW else 1f
        val width = (fullW * scale).toInt().coerceAtLeast(1)
        val height = (fullH * scale).toInt().coerceAtLeast(1)
        val dpi = (metrics.densityDpi * scale).toInt().coerceAtLeast(1)
        val latch = CountDownLatch(1)
        val posted = CountDownLatch(1)
        var setupError: Exception? = null
        val ok = handler.post {
            try {
                synchronized(sessionLock) {
                    pendingShot = null
                    grabLatch = latch
                    ensureMirrorLocked(width, height, dpi, handler)
                }
            } catch (e: Exception) {
                setupError = e
                latch.countDown()
            } finally {
                posted.countDown()
            }
        }
        if (!ok) error("capture handler gone")
        posted.await(5, TimeUnit.SECONDS)
        setupError?.let { throw it }
        latch.await(5, TimeUnit.SECONDS)
        return synchronized(sessionLock) { pendingShot } ?: error("no frame")
    }

    private fun onMirrorImage(imageReader: ImageReader) {
        val latch = synchronized(sessionLock) { grabLatch } ?: run {
            imageReader.acquireLatestImage()?.close()
            return
        }
        imageReader.acquireLatestImage()?.use { image ->
            val captured = toCapturedScreen(image)
            synchronized(sessionLock) {
                if (grabLatch === latch) {
                    pendingShot = captured
                    grabLatch = null
                }
            }
            latch.countDown()
        }
    }

    private fun ensureMirrorLocked(width: Int, height: Int, dpi: Int, handler: Handler) {
        if (sessionDisplay != null && sessionReader != null) return
        val proj = projection ?: error("media projection unavailable")
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ onMirrorImage(it) }, handler)
        sessionReader = reader
        sessionDisplay = proj.createVirtualDisplay(
            "dougie-capture",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
    }

    private fun ensureProjectionLocked(): MediaProjection {
        projection?.let { return it }
        val token = ScreenCaptureConsentStore.data
            ?: error("missing projection token")
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val created = try {
            projectionManager.getMediaProjection(
                ScreenCaptureConsentStore.resultCode,
                Intent(token),
            )
        } catch (_: Exception) {
            ScreenCaptureConsentStore.clear()
            error("media projection unavailable")
        }
        if (created == null) {
            ScreenCaptureConsentStore.clear()
            error("media projection unavailable")
        }
        val thread = HandlerThread("dougie-screen-capture").apply { start() }
        val handler = Handler(thread.looper)
        captureThread = thread
        captureHandler = handler
        created.registerCallback(sessionCallback, handler)
        projection = created
        return created
    }

    private fun dropHardware(clearToken: Boolean) {
        val handler: Handler?
        val thread: HandlerThread?
        val proj: MediaProjection?
        val display: VirtualDisplay?
        val reader: ImageReader?
        val waiting: CountDownLatch?
        synchronized(sessionLock) {
            handler = captureHandler
            thread = captureThread
            proj = projection
            display = sessionDisplay
            reader = sessionReader
            waiting = grabLatch
            captureHandler = null
            captureThread = null
            projection = null
            sessionDisplay = null
            sessionReader = null
            grabLatch = null
            pendingShot = null
        }
        waiting?.countDown()
        val finishOnMain = {
            mainHandler.post {
                if (fgsStarted) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    fgsStarted = false
                }
                ScreenCaptureSession.foreground = false
                stopSelf()
            }
        }
        val teardown = {
            try {
                runCatching { display?.release() }
                runCatching { reader?.close() }
                if (proj != null) {
                    runCatching { proj.unregisterCallback(sessionCallback) }
                    if (clearToken) {
                        runCatching { proj.stop() }
                    }
                }
            } finally {
                thread?.quitSafely()
                if (clearToken) {
                    ScreenCaptureConsentStore.clear()
                }
                finishOnMain()
            }
        }
        val onHandler = handler != null && Looper.myLooper() == handler.looper
        if (onHandler) {
            teardown()
        } else {
            val queued = handler?.post { teardown() } ?: false
            if (!queued) teardown()
        }
    }

    private fun toCapturedScreen(image: Image): CapturedScreen {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val gray = ByteArray(width * height)
        val argb = IntArray(width * height)
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
                val index = y * width + x
                gray[index] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
                argb[index] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(argb, 0, width, 0, 0, width, height)
        val jpeg = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)
        bitmap.recycle()
        return CapturedScreen(
            frame = ScreenFrame(
                id = UUID.randomUUID().toString(),
                width = width,
                height = height,
                gray = gray,
            ),
            previewJpeg = jpeg.toByteArray(),
        )
    }

    companion object {
        const val ACTION_STOP = "com.dougie.tool.system.STOP_PROJECTION"
        private const val CHANNEL_ID = "dougie_screen_capture"
        private const val NOTIFICATION_ID = 47
        private const val MAX_CAPTURE_WIDTH = 720
        private const val JPEG_QUALITY = 75
    }
}
