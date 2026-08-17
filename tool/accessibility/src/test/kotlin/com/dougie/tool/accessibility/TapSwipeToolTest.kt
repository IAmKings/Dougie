package com.dougie.tool.accessibility

import com.dougie.core.model.AgentException
import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.InMemoryIdempotencyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapSwipeToolTest {
    @Test
    fun executeWithoutConsentDoesNotTap() = runBlocking {
        val port = FakeGesturePort()
        val tool = TapSwipeTool(consentGranted = { false }, port = port)
        val result = tool.execute(TAP_JSON, ctx())
        assertEquals(TapSwipeTool.CONSENT_REQUIRED, result.error)
        assertEquals(0, port.taps)
    }

    @Test
    fun executeWithoutServiceDoesNotTap() = runBlocking {
        val port = FakeGesturePort(connected = false)
        val tool = TapSwipeTool(consentGranted = { true }, port = port)
        val result = tool.execute(TAP_JSON, ctx())
        assertEquals(TapSwipeTool.SERVICE_REQUIRED, result.error)
        assertEquals(0, port.taps)
    }

    @Test
    fun executeBlocksPaymentPackage() = runBlocking {
        val port = FakeGesturePort(foreground = "com.eg.android.AlipayGphone")
        val tool = TapSwipeTool(consentGranted = { true }, port = port)
        val result = tool.execute(TAP_JSON, ctx())
        assertEquals(TapSwipeTool.BLOCKED_APP, result.error)
        assertEquals(0, port.taps)
    }

    @Test
    fun executeTapsOnceAndIsIdempotent() = runBlocking {
        val port = FakeGesturePort(foreground = "com.android.chrome")
        val store = InMemoryIdempotencyStore()
        val tool = TapSwipeTool(consentGranted = { true }, port = port, idempotencyStore = store)
        val first = tool.execute(TAP_JSON, ctx())
        val second = tool.execute(TAP_JSON, ctx())
        assertTrue(first.json.contains("\"ok\":true"))
        assertEquals(first.json, second.json)
        assertEquals(1, port.taps)
        assertEquals(0, port.swipes)
    }

    @Test
    fun executeSwipeRecordsCoordinates() = runBlocking {
        val port = FakeGesturePort(foreground = "com.android.chrome")
        val tool = TapSwipeTool(consentGranted = { true }, port = port)
        val result = tool.execute(
            """{"action":"swipe","x":10,"y":20,"x2":30,"y2":40,"durationMs":120}""",
            ctx(),
        )
        assertTrue(result.json.contains("\"ok\":true"))
        assertEquals(1, port.swipes)
        assertEquals(listOf(10, 20, 30, 40, 120), port.lastSwipe)
    }

    @Test
    fun validateRejectsUnknownAction() {
        val tool = TapSwipeTool(consentGranted = { true }, port = FakeGesturePort())
        try {
            tool.validateArguments("""{"action":"pinch","x":1,"y":1}""")
            throw AssertionError("expected AgentException")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
    }

    @Test
    fun highRiskTokensCoverPasswordManagers() {
        assertTrue(HighRiskForeground.isBlocked("com.bitwarden.app"))
        assertTrue(HighRiskForeground.isBlocked(null))
        assertFalse(HighRiskForeground.isBlocked("com.android.chrome"))
    }

    private fun ctx() = ToolContext(taskId = "t", toolCallId = "c")

    private class FakeGesturePort(
        private val connected: Boolean = true,
        private val foreground: String? = "com.android.chrome",
        var succeed: Boolean = true,
    ) : GesturePort {
        var taps = 0
        var swipes = 0
        var lastSwipe: List<Int> = emptyList()

        override fun isConnected(): Boolean = connected
        override fun foregroundPackage(): String? = foreground
        override suspend fun tap(x: Int, y: Int): Boolean {
            taps += 1
            return succeed
        }

        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
            swipes += 1
            lastSwipe = listOf(x1, y1, x2, y2, durationMs)
            return succeed
        }
    }

    companion object {
        private const val TAP_JSON = """{"action":"tap","x":8,"y":12}"""
    }
}
