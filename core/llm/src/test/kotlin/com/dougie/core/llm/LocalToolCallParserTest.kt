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
