package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalToolCallParserTest {
    @Test
    fun validTimeJsonBecomesToolCall() {
        val event = LocalToolCallParser.parse("""{"name":"time","args":{}}""")
        val call = event as LlmEvent.ToolCall
        assertEquals("time", call.name)
        assertEquals("{}", call.argsJson)
        assertEquals("", call.id)
    }

    @Test
    fun invalidJsonIsText() {
        assertNull(LocalToolCallParser.parse("{name:time}"))
    }

    @Test
    fun batteryAndClipboardJsonParse() {
        val battery = LocalToolCallParser.parse("""{"name":"battery","args":{}}""") as LlmEvent.ToolCall
        assertEquals("battery", battery.name)
        val clip = LocalToolCallParser.parse("""{"name":"clipboard_read","args":{}}""") as LlmEvent.ToolCall
        assertEquals("clipboard_read", clip.name)
        assertEquals("{}", clip.argsJson)
        val loc = LocalToolCallParser.parse("""{"name":"location","args":{}}""") as LlmEvent.ToolCall
        assertEquals("location", loc.name)
        listOf("calendar_query", "screen_capture", "speech_input").forEach { name ->
            val call = LocalToolCallParser.parse("""{"name":"$name","args":{}}""") as LlmEvent.ToolCall
            assertEquals(name, call.name)
            assertEquals("{}", call.argsJson)
        }
        val write = LocalToolCallParser.parse(
            """{"name":"clipboard_write","args":{"text":"你好"}}""",
        ) as LlmEvent.ToolCall
        assertEquals("clipboard_write", write.name)
        assertTrue(write.argsJson.contains("你好"))
        val create = LocalToolCallParser.parse(
            """{"name":"calendar_create","args":{"title":"开会","startIso":"2026-09-08T15:00:00+08:00"}}""",
        ) as LlmEvent.ToolCall
        assertEquals("calendar_create", create.name)
        assertTrue(create.argsJson.contains("startIso"))
        val open = LocalToolCallParser.parse(
            """{"name":"app_intent","args":{"uri":"https://example.com"}}""",
        ) as LlmEvent.ToolCall
        assertEquals("app_intent", open.name)
        assertTrue(open.argsJson.contains("example.com"))
    }

    @Test
    fun markdownFenceJsonParses() {
        val event = LocalToolCallParser.parse(
            """
            ```json
            {"name":"battery","args":{}}
            ```
            """.trimIndent(),
        ) as LlmEvent.ToolCall
        assertEquals("battery", event.name)
    }

    @Test
    fun mixedChineseIsText() {
        assertNull(LocalToolCallParser.parse("""现在几点了 {"name":"time","args":{}}"""))
    }

    @Test
    fun unknownNameStillParses() {
        val event = LocalToolCallParser.parse("""{"name":"not_a_tool","args":{"x":1}}""")
        val call = event as LlmEvent.ToolCall
        assertEquals("not_a_tool", call.name)
        assertTrue(call.argsJson.contains("x"))
    }

    @Test
    fun missingArgsDefaultsToEmptyObject() {
        val event = LocalToolCallParser.parse("""{"name":"time"}""")
        val call = event as LlmEvent.ToolCall
        assertEquals("time", call.name)
        assertEquals("{}", call.argsJson)
    }

    @Test
    fun repeatOfSuccessfulCallIsDetected() {
        val call = LocalToolCallParser.parse("""{"name":"time","args":{}}""") as LlmEvent.ToolCall
        val task = AgentTask(
            taskId = "t",
            input = "现在几点了",
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "c1",
                    toolName = "time",
                    argsSummary = "{}",
                    resultJson = """{"iso":"2026-09-06T12:00:00"}""",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
        )
        assertTrue(LocalToolCallParser.isRepeatOfSuccessfulCall(task, call))
        assertTrue(
            !LocalToolCallParser.isRepeatOfSuccessfulCall(
                AgentTask(taskId = "t0", input = "现在几点了"),
                call,
            ),
        )
    }
}
