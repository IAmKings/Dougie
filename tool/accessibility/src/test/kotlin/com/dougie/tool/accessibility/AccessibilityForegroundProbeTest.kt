package com.dougie.tool.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityForegroundProbeTest {

    @Test
    fun returnsTrimmedForegroundPackageFromGesturePort() {
        val probe = AccessibilityForegroundProbe(FakePort(" com.example.app "))
        assertEquals("com.example.app", probe.currentForegroundPackage())
    }

    @Test
    fun mapsEmptyAndNullForegroundToUnknown() {
        assertEquals(null, AccessibilityForegroundProbe(FakePort("")).currentForegroundPackage())
        assertEquals(null, AccessibilityForegroundProbe(FakePort("   ")).currentForegroundPackage())
        assertEquals(null, AccessibilityForegroundProbe(FakePort(null)).currentForegroundPackage())
    }

    private class FakePort(private val value: String?) : GesturePort {
        override fun isConnected(): Boolean = value != null
        override fun foregroundPackage(): String? = value
        override suspend fun tap(x: Int, y: Int): Boolean = false
        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean = false
    }
}
