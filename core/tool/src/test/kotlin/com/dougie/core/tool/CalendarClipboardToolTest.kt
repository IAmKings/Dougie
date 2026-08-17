package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarCreateToolTest {

    @Test
    fun sameIdempotencyKeyInsertsOnce() = runTest {
        val port = FakeCalendarPort()
        val tool = CalendarCreateTool(port)
        val context = ToolContext(taskId = "task-1", toolCallId = "call-1")
        val args = """{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}"""
        val first = tool.execute(args, context)
        val second = tool.execute(args, context)
        assertEquals(first.json, second.json)
        assertEquals(1, port.createCalls.size)
        assertEquals(context.idempotencyKey, port.createCalls.single().idempotencyKey)
    }
}

class ClipboardReadToolTest {

    @Test
    fun backgroundReadReturnsForegroundError() = runTest {
        val port = FakeClipboardPort(foreground = false, text = "secret")
        val tool = ClipboardReadTool(port)
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.CLIPBOARD_NOT_FOREGROUND, result.error)
        assertEquals(true, result.isFatal)
        assertEquals(true, result.json.contains(UserFacingErrors.CLIPBOARD_NOT_FOREGROUND))
        assertEquals(false, result.json.contains("secret"))
    }
}
