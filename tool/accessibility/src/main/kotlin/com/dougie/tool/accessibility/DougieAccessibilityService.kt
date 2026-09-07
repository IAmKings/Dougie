package com.dougie.tool.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class DougieAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
            lastForegroundPackage = null
        }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.trim().orEmpty()
        if (pkg.isEmpty()) return
        val lower = pkg.lowercase()
        if (lower.contains("inputmethod") || lower.endsWith(".tts")) return
        lastForegroundPackage = pkg
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: DougieAccessibilityService? = null
            internal set

        @Volatile
        var lastForegroundPackage: String? = null
            internal set
    }
}
