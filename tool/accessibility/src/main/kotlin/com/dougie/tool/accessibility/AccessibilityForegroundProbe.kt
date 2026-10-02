package com.dougie.tool.accessibility

import com.dougie.core.tool.ForegroundProbePort

/** Exposes the accessibility service's foreground package as a verification observer. */
class AccessibilityForegroundProbe(
    private val gesturePort: GesturePort,
) : ForegroundProbePort {
    override fun currentForegroundPackage(): String? =
        gesturePort.foregroundPackage()?.trim()?.ifEmpty { null }
}
