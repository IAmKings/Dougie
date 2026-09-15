package com.dougie.core.model

import org.junit.Assert.assertEquals
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
}
