package com.dougie.feature.chat

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {

    @Test
    fun preparingShowsUserThenThinkingWithLoopNumber() {
        val state = AgentTask(taskId = "t", input = BATTERY_EXAMPLE, status = TaskStatus.THINKING)
            .toChatUiState()
        assertEquals(
            listOf("user", "thinking-1"),
            state.items.map { it.kind() },
        )
        val thinking = state.items[1] as ChatItem.Thinking
        assertEquals(1, thinking.loopNumber)
    }

    @Test
    fun completedShowsUserThinkingToolChainThenFinal() {
        val tools = (1..3).map { n ->
            ToolTraceEntry(
                toolCallId = "battery-$n",
                toolName = "battery",
                argsSummary = "{}",
                resultJson = """{"battery_percent":63,"charging":true}""",
                status = ToolTraceStatus.SUCCESS,
            )
        }
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.COMPLETED,
            loopCount = 3,
            toolTrace = tools,
            finalAnswer = "你现在的手机电量是 63%。",
        ).toChatUiState()

        assertEquals(
            listOf(
                "user",
                "thinking-1",
                "tool-battery-1",
                "thinking-2",
                "tool-battery-2",
                "thinking-3",
                "tool-battery-3",
                "agent",
            ),
            state.items.map { it.kind() },
        )
        assertTrue(state.inputEnabled)
        assertTrue((state.items.last() as ChatItem.AgentMessage).text.contains("63"))
    }

    @Test
    fun failedShowsUserFacingEgressText() {
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.EGRESS_BLOCKED,
        ).toChatUiState()
        val message = state.items.last() as ChatItem.AgentMessage
        assertTrue(message.text.contains(UserFacingErrors.EGRESS_BLOCKED))
        assertTrue(state.inputEnabled)
    }

    private fun ChatItem.kind(): String = when (this) {
        is ChatItem.UserMessage -> "user"
        is ChatItem.Thinking -> "thinking-$loopNumber"
        is ChatItem.ToolCard -> "tool-${entry.toolCallId}"
        is ChatItem.AgentMessage -> "agent"
    }
}
