package com.dougie.feature.debug

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.runtime.AuditEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DebugUiStateTest {
    @Test
    fun mapsTaskIdStatusLoopCountAndLastErrorOnly() {
        val snapshot = AgentTask(
            taskId = "t1",
            input = "secret user prompt",
            status = TaskStatus.FAILED,
            loopCount = 3,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "c1",
                    toolName = "battery",
                    argsSummary = "{\"hidden\":true}",
                    resultJson = "{\"battery_percent\":63}",
                ),
            ),
            lastError = "任务失败",
        ).toDebugTaskSnapshot()
        assertEquals("t1", snapshot.taskId)
        assertEquals("FAILED", snapshot.status)
        assertEquals(3, snapshot.loopCount)
        assertEquals("任务失败", snapshot.lastError)
        val dumped = snapshot.toString()
        assertFalse(dumped.contains("secret user prompt"))
        assertFalse(dumped.contains("battery_percent"))
        assertFalse(dumped.contains("hidden"))
    }

    @Test
    fun mapsAuditEntryWithoutArgs() {
        val row = AuditEntry(
            taskId = "t2",
            toolName = "time",
            outcome = "SUCCESS",
            createdAt = 1L,
        ).toDebugAuditRow()
        assertEquals("t2", row.taskId)
        assertEquals("time", row.toolName)
        assertEquals("SUCCESS", row.outcome)
        assertEquals(1L, row.createdAt)
    }

    @Test
    fun uiModelsDoNotDeclareResultJsonOrPrompt() {
        val names = listOf(
            DebugTaskSnapshot::class.java,
            DebugAuditRow::class.java,
            DebugUiState::class.java,
        ).flatMap { type -> type.declaredFields.map { it.name } }
        assertFalse(names.any { it.contains("resultJson", ignoreCase = true) })
        assertFalse(names.any { it.contains("prompt", ignoreCase = true) })
        assertFalse(names.any { it.contains("args", ignoreCase = true) })
        assertFalse(names.any { it.contains("streaming", ignoreCase = true) })
        assertFalse(names.any { it.contains("finalAnswer", ignoreCase = true) })
        assertFalse(names.any { it.contains("toolTrace", ignoreCase = true) })
        assertNull(DebugTaskSnapshot::class.java.declaredFields.find { it.name == "input" })
    }
}
