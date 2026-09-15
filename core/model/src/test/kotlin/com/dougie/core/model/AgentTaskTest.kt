package com.dougie.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskTest {
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
}
