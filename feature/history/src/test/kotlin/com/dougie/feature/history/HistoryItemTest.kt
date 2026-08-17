package com.dougie.feature.history

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryItemTest {
    @Test
    fun mapsPersistedTaskFields() {
        val item = AgentTask(
            taskId = "t1",
            input = "帮我约明天下午开会并且再确认一下地点和时间安排",
            status = TaskStatus.FAILED,
            loopCount = 2,
            toolTrace = listOf(
                ToolTraceEntry(toolCallId = "c1", toolName = "calendar_query", argsSummary = "{}"),
                ToolTraceEntry(toolCallId = "c2", toolName = "calendar_create", argsSummary = "{}"),
            ),
            lastError = UserFacingErrors.INTERRUPTED,
        ).toHistoryItem(maxInputChars = 8)
        assertEquals("帮我约明天下午开…", item.inputSummary)
        assertEquals("失败", item.statusLabel)
        assertEquals(2, item.loopCount)
        assertEquals("calendar_query → calendar_create", item.toolChain)
        assertEquals(UserFacingErrors.INTERRUPTED, item.error)
    }
}
