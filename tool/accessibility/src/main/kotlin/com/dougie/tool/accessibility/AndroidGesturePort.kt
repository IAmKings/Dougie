package com.dougie.tool.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidGesturePort : GesturePort {
    override fun isConnected(): Boolean = DougieAccessibilityService.instance != null

    override fun foregroundPackage(): String? {
        val service = DougieAccessibilityService.instance ?: return null
        return service.rootInActiveWindow?.packageName?.toString()
    }

    override suspend fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatch(path, TAP_MS)
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return dispatch(path, durationMs.toLong())
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean {
        val service = DougieAccessibilityService.instance ?: return false
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val started = service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null,
                )
                if (!started && cont.isActive) {
                    cont.resume(false)
                }
            }
        } ?: false
    }

    companion object {
        private const val TAP_MS = 50L
        private const val GESTURE_TIMEOUT_MS = 5_000L
    }
}
