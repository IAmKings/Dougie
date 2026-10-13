package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskNoticeConfirmSummaryTest {

    private fun awaiting(args: String?, tool: String = "tap_by_text"): AgentTask {
        val entry = ToolTraceEntry(
            toolCallId = "c1",
            toolName = tool,
            argsSummary = args ?: "{}",
            status = ToolTraceStatus.PENDING,
        )
        return AgentTask(taskId = "t", input = "i", status = TaskStatus.AWAITING_CONFIRMATION, toolTrace = listOf(entry))
    }

    @Test
    fun tapByTextInputSummarizesNeedle() {
        val summary = confirmSummary(awaiting("""{"text":"记录"}"""))
        assertEquals("点击“记录”", summary)
        assertEquals(
            "待确认 · 点击“记录”",
            formatTaskNotice(awaiting("""{"text":"记录"}""")),
        )
    }

    @Test
    fun nonTextToolsAndBrokenArgsReturnNull() {
        assertNull(confirmSummary(awaiting("{}", tool = "tap_by_text")))
        assertNull(confirmSummary(awaiting("not-json", tool = "tap_by_text")))
        assertNull(confirmSummary(awaiting("""{"to":"1"}""", tool = "sms_compose")))
        assertNull(confirmSummary(awaiting("""{"text":"x"}""").copy(status = TaskStatus.THINKING)))
    }
}
