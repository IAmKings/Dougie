package com.dougie.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DougieOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var ball: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var scrim: View? = null
    private var menu: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile
    private var capturing = false
    private var expanded = false

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
                    if (abs(dx) > slop || abs(dy) > slop) {
                        if (!dragged && expanded) collapse()
                        dragged = true
                    }
                    layout.x = startX + dx.toInt()
                    layout.y = startY + dy.toInt()
                    windowManager?.updateViewLayout(view, layout)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragged) {
                        if (expanded) collapse() else expand()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun expand() {
        val wm = windowManager ?: return
        val ballLayout = params ?: return
        if (expanded || capturing) return
        val density = resources.displayMetrics.density
        val scrimView = View(this).apply {
            setBackgroundColor(0x33000000)
            setOnClickListener { collapse() }
            contentDescription = getString(R.string.overlay_dismiss_menu)
        }
        val scrimLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF3D5198.toInt())
            setPadding(
                (12 * density).toInt(),
                (8 * density).toInt(),
                (12 * density).toInt(),
                (8 * density).toInt(),
            )
            addView(actionRow(getString(R.string.overlay_capture)) { captureThenOpenChat() })
            addView(actionRow(getString(R.string.overlay_open_chat)) { openChatOnly() })
        }
        val menuLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        menuLp.gravity = Gravity.TOP or Gravity.START
        val ballHeight = ball?.height ?: (40 * density).toInt()
        menuLp.x = ballLayout.x
        menuLp.y = ballLayout.y + ballHeight + (8 * density).toInt()
        val scrimOk = runCatching { wm.addView(scrimView, scrimLp) }.isSuccess
        val menuOk = scrimOk && runCatching { wm.addView(panel, menuLp) }.isSuccess
        if (!menuOk) {
            runCatching { wm.removeView(scrimView) }
            runCatching { wm.removeView(panel) }
            return
        }
        ball?.let { b ->
            runCatching { wm.removeView(b) }
            runCatching { wm.addView(b, ballLayout) }
        }
        scrim = scrimView
        menu = panel
        expanded = true
    }

    private fun actionRow(label: String, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(
                (8 * density).toInt(),
                (10 * density).toInt(),
                (8 * density).toInt(),
                (10 * density).toInt(),
            )
            contentDescription = label
            isClickable = true
            @SuppressLint("ClickableViewAccessibility")
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> true
                    MotionEvent.ACTION_UP -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun collapse() {
        val wm = windowManager
        scrim?.let { runCatching { wm?.removeView(it) } }
        menu?.let { runCatching { wm?.removeView(it) } }
        scrim = null
        menu = null
        expanded = false
    }

    private fun openOverlayPermission() {
        val uri = Uri.parse("package:$packageName")
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun openChatOnly() {
        collapse()
        launchDougie(chatLaunchIntent(this, applyPinnedScreen = false), requestCode = 49)
    }

    private fun captureThenOpenChat() {
        if (capturing) return
        if (!Settings.canDrawOverlays(this)) {
            collapse()
            Toast.makeText(this, UserFacingErrors.PERMISSION_DENIED, Toast.LENGTH_LONG).show()
            openOverlayPermission()
            return
        }
        val app = application as DougieApplication
        if (!app.screenCapturePort.hasProjectionConsent()) {
            collapse()
            app.overlayAttachError = UserFacingErrors.PERMISSION_DENIED
            Toast.makeText(this, getString(R.string.overlay_need_projection), Toast.LENGTH_LONG).show()
            launchDougie(
                chatLaunchIntent(this, applyPinnedScreen = true, openPermissions = true),
                requestCode = 48,
            )
            return
        }
        capturing = true
        scope.launch {
            try {
                collapse()
                ball?.visibility = View.INVISIBLE
                val result = withContext(Dispatchers.Default) {
                    app.pinCurrentScreen(requireForeground = false)
                }
                result.fold(
                    onSuccess = { app.overlayAttachError = null },
                    onFailure = { error ->
                        app.screenFrameStore.clearPin()
                        app.overlayAttachError = (error as? AgentException)?.userMessage
                            ?: UserFacingErrors.TOOL_FAILED
                    },
                )
            } finally {
                ball?.visibility = View.VISIBLE
                capturing = false
                val denied = app.overlayAttachError == UserFacingErrors.PERMISSION_DENIED
                if (denied) {
                    Toast.makeText(
                        this@DougieOverlayService,
                        getString(R.string.overlay_need_projection),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                launchDougie(
                    chatLaunchIntent(
                        this@DougieOverlayService,
                        applyPinnedScreen = true,
                        openPermissions = denied,
                    ),
                    requestCode = if (denied) 48 else 47,
                )
            }
        }
    }

    private fun launchDougie(intent: Intent, requestCode: Int) {
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val pending = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sent = runCatching { pending.send() }.isSuccess
        if (!sent) {
            runCatching { startActivity(intent) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        collapse()
        ball?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        ball = null
        windowManager = null
        super.onDestroy()
    }
}
