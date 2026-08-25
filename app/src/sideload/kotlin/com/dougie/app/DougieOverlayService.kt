package com.dougie.app

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class DougieOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var ball: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density
        val view = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 14f
            setPadding(
                (16 * density).toInt(),
                (10 * density).toInt(),
                (16 * density).toInt(),
                (10 * density).toInt(),
            )
            setBackgroundColor(0xFF3D5198.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            contentDescription = getString(R.string.app_name)
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        layout.gravity = Gravity.TOP or Gravity.START
        layout.x = (16 * density).toInt()
        layout.y = (120 * density).toInt()
        attachTouch(view, layout)
        val attached = runCatching { wm.addView(view, layout) }.isSuccess
        if (!attached) {
            stopSelf()
            return
        }
        ball = view
        params = layout
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(view: View, layout: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        val slop = view.resources.displayMetrics.density * 8
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layout.x
                    startY = layout.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    layout.x = startX + dx.toInt()
                    layout.y = startY + dy.toInt()
                    windowManager?.updateViewLayout(view, layout)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragged) {
                        startActivity(chatLaunchIntent(this))
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        ball?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        ball = null
        windowManager = null
        super.onDestroy()
    }
}
