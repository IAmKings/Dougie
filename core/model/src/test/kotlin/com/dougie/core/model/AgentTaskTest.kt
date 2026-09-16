package com.dougie.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskTest {
    @Test
    fun retrievedConversationHitsDefaultEmptyAndCopyKeepsThem() {
        val task = AgentTask(taskId = "t", input = "hi")
        assertTrue(task.retrievedConversationHits.isEmpty())
        val hits = listOf(
            ConversationHit(
                taskId = "old",
                conversationId = "window-a",
                sourceLabel = "工作 · UNO 项目关键点",
                user = "UNO 项目关键点是本地优先",
                assistant = "记下了：本地优先。",
            ),
        )
        val thinking = task.copy(status = TaskStatus.THINKING, retrievedConversationHits = hits)
        assertEquals(hits, thinking.retrievedConversationHits)
        assertEquals(hits, thinking.copy(streamingText = "x").retrievedConversationHits)
    }

    @Test
    fun priorTurnsDefaultEmptyAndCopyKeepsThem() {
        val task = AgentTask(taskId = "t", input = "hi")
        assertTrue(task.priorTurns.isEmpty())
        val turns = listOf(ConversationTurn("我同事叫张伟", "好的，他叫张伟。"))
        val thinking = task.copy(status = TaskStatus.THINKING, priorTurns = turns)
        assertEquals(turns, thinking.priorTurns)
        assertEquals(turns, thinking.copy(streamingText = "x").priorTurns)
    }

    @Test
    fun timestampsDefaultNullAndCopyKeepsThem() {
        val task = AgentTask(taskId = "t", input = "hi")
        assertNull(task.startedAt)
        assertNull(task.endedAt)
        val started = task.copy(startedAt = 100L)
        assertEquals(100L, started.startedAt)
        assertNull(started.endedAt)
        val ended = started.copy(status = TaskStatus.COMPLETED, endedAt = 250L)
        assertEquals(100L, ended.startedAt)
        assertEquals(250L, ended.endedAt)
        val copied = ended.copy(streamingText = "x")
        assertEquals(100L, copied.startedAt)
        assertEquals(250L, copied.endedAt)
    }

    @Test
    fun completionPathUserLabels() {
        assertEquals("本地意图", CompletionPath.LOCAL_INTENT.toUserLabel())
        assertEquals("本地 LLM", CompletionPath.LOCAL_LLM.toUserLabel())
        assertEquals("远程 LLM", CompletionPath.REMOTE_LLM.toUserLabel())
    }

    @Test
    fun formatTaskDurationCoversBucketsAndNegative() {
        assertNull(formatTaskDuration(null, 1_000L))
        assertNull(formatTaskDuration(1_000L, null))
        assertEquals("不足1秒", formatTaskDuration(1_000L, 1_500L))
        assertEquals("3秒", formatTaskDuration(0L, 3_000L))
        assertEquals("59秒", formatTaskDuration(0L, 59_000L))
        assertEquals("1分", formatTaskDuration(0L, 60_000L))
        assertEquals("1分12秒", formatTaskDuration(0L, 72_000L))
        assertEquals("不足1秒", formatTaskDuration(5_000L, 1_000L))
    }
}
