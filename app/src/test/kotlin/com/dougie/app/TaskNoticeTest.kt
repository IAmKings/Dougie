package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskNoticeTest {
    @Test
    fun idleAndNullCancel() {
        assertNull(formatTaskNotice(null))
        assertNull(formatTaskNotice(AgentTask("t", "prompt", TaskStatus.IDLE)))
        assertFalse(isTaskBusy(null))
        assertFalse(isTaskBusy(AgentTask("t", "prompt", TaskStatus.IDLE)))
    }

    @Test
    fun thinkingAndToolAndConfirm() {
        assertEquals(
            "思考中 · 循环 2",
            formatTaskNotice(AgentTask("t", "secret", TaskStatus.THINKING, loopCount = 2)),
        )
        val withTool = AgentTask(
            taskId = "t",
            input = "secret",
            status = TaskStatus.TOOL_EXECUTING,
            loopCount = 2,
            toolTrace = listOf(
                ToolTraceEntry("c", "battery", argsSummary = "percent=1", status = ToolTraceStatus.SUCCESS),
            ),
        )
        assertEquals("工具 · 循环 2 · battery", formatTaskNotice(withTool))
        assertFalse(formatTaskNotice(withTool)!!.contains("percent"))
        assertEquals(
            "待确认 · calendar_create",
            formatTaskNotice(
                AgentTask(
                    "t",
                    "secret",
                    TaskStatus.AWAITING_CONFIRMATION,
                    toolTrace = listOf(
                        ToolTraceEntry("c", "calendar_create", argsSummary = "title=x"),
                    ),
                ),
            ),
        )
        assertTrue(isTaskBusy(AgentTask("t", "x", TaskStatus.THINKING)))
    }

    @Test
    fun failedOmitsErrorAndPrompt() {
        val failed = AgentTask(
            taskId = "t",
            input = "我的密钥是 abc",
            status = TaskStatus.FAILED,
            loopCount = 3,
            lastError = "出境被拦截",
            finalAnswer = "不该出现",
            streamingText = "流式草稿",
        )
        val line = formatTaskNotice(failed)
        assertEquals("任务失败 · 循环 3", line)
        assertFalse(line!!.contains("密钥"))
        assertFalse(line.contains("拦截"))
        assertFalse(line.contains("不该出现"))
        assertFalse(line.contains("流式"))
        assertEquals("已完成 · 循环 1", formatTaskNotice(AgentTask("t", "x", TaskStatus.COMPLETED, loopCount = 1)))
        assertFalse(isTaskBusy(failed))
    }

    @Test
    fun bubblePendingIntentIsMutableOnApi31() {
        val flags = taskNoticeBubblePendingFlags(31)
        assertTrue(flags and android.app.PendingIntent.FLAG_MUTABLE != 0)
        assertEquals(0, flags and android.app.PendingIntent.FLAG_IMMUTABLE)
        val preS = taskNoticeBubblePendingFlags(30)
        assertEquals(0, preS and android.app.PendingIntent.FLAG_IMMUTABLE)
        assertEquals(0, preS and android.app.PendingIntent.FLAG_MUTABLE)
    }
}
