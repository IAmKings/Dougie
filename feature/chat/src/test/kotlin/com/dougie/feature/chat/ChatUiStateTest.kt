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
        assertEquals(false, state.canRetry)
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
        assertTrue(state.canRetry)
    }

    @Test
    fun interruptedFailedShowsRetry() {
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.INTERRUPTED,
        ).toChatUiState()
        assertTrue(state.inputEnabled)
        assertTrue(state.canRetry)
        assertTrue((state.items.last() as ChatItem.AgentMessage).text.contains(UserFacingErrors.INTERRUPTED))
    }

    @Test
    fun thinkingShowsStreamingTextBeforeCompletion() {
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.THINKING,
            streamingText = "你现在的手机",
        ).toChatUiState()
        assertEquals(listOf("user", "thinking-1", "agent"), state.items.map { it.kind() })
        assertEquals("你现在的手机", (state.items.last() as ChatItem.AgentMessage).text)
        assertEquals(false, state.inputEnabled)
    }

    @Test
    fun toolCardsUseGenericNamesInsteadOfHardcodedBattery() {
        assertEquals("电池工具", toolDisplayName("battery"))
        assertEquals("时间工具", toolDisplayName("time"))
        assertEquals("calendar", toolDisplayName("calendar"))
    }

    @Test
    fun awaitingConfirmationShowsConfirmCardAndDisablesInput() {
        val state = AgentTask(
            taskId = "t",
            input = "帮我约明天下午开会",
            status = TaskStatus.AWAITING_CONFIRMATION,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "cal-1",
                    toolName = "calendar_create",
                    argsSummary = """{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}""",
                    status = ToolTraceStatus.PENDING,
                    riskLevel = com.dougie.core.model.RiskLevel.L2,
                ),
            ),
        ).toChatUiState()
        assertEquals(listOf("user", "thinking-1", "confirm-cal-1"), state.items.map { it.kind() })
        val card = state.items.last() as ChatItem.ConfirmCard
        assertEquals("calendar_create", card.toolName)
        assertEquals("""{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}""", card.argsJson)
        assertEquals(com.dougie.core.model.RiskLevel.L2, card.riskLevel)
        assertEquals(false, state.inputEnabled)
    }

    private fun ChatItem.kind(): String = when (this) {
        is ChatItem.UserMessage -> "user"
        is ChatItem.Thinking -> "thinking-$loopNumber"
        is ChatItem.ToolCard -> "tool-${entry.toolCallId}"
        is ChatItem.ConfirmCard -> "confirm-$toolCallId"
        is ChatItem.AgentMessage -> "agent"
    }
}
