package com.dougie.feature.chat

import com.dougie.core.model.AgentTask
import com.dougie.core.model.MemoryEntry
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
        listOf(TaskStatus.PREPARING, TaskStatus.THINKING).forEach { status ->
            val state = AgentTask(taskId = "t", input = BATTERY_EXAMPLE, status = status)
                .toChatUiState()
            assertEquals(
                listOf("user", "thinking-1"),
                state.items.map { it.kind() },
            )
            val thinking = state.items[1] as ChatItem.Thinking
            assertEquals(1, thinking.loopNumber)
            assertEquals(true, thinking.live)
        }
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
        assertEquals(emptyList<String>(), (state.items.last() as ChatItem.AgentMessage).memorySources)
        assertEquals(false, state.canRetry)
        val past = state.items.filterIsInstance<ChatItem.Thinking>()
        assertEquals(listOf(false, false, false), past.map { it.live })
    }

    @Test
    fun afterToolSuccessLiveThinkingIsOnlyTheNextLoop() {
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.THINKING,
            loopCount = 1,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "battery-1",
                    toolName = "battery",
                    argsSummary = "{}",
                    resultJson = """{"battery_percent":63,"charging":true}""",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
        ).toChatUiState()
        val chips = state.items.filterIsInstance<ChatItem.Thinking>()
        assertEquals(listOf(1, 2), chips.map { it.loopNumber })
        assertEquals(listOf(false, true), chips.map { it.live })
    }

    @Test
    fun completedWithRetrievedMemoriesPutsUniqueSourcesOnFinalAnswer() {
        val state = AgentTask(
            taskId = "t",
            input = "我叫什么",
            status = TaskStatus.COMPLETED,
            finalAnswer = "你叫小明。",
            retrievedMemories = listOf(
                fact("m1", source = "task-0"),
                fact("m2", source = "task-1"),
                fact("m3", source = "task-0"),
                fact("m4", source = "  "),
            ),
        ).toChatUiState()
        val message = state.items.last() as ChatItem.AgentMessage
        assertEquals("你叫小明。", message.text)
        assertEquals(listOf("task-0", "task-1"), message.memorySources)

        val blankOnly = AgentTask(
            taskId = "t",
            input = "我叫什么",
            status = TaskStatus.COMPLETED,
            finalAnswer = "你叫小明。",
            retrievedMemories = listOf(fact("m1", source = "   ")),
        ).toChatUiState()
        assertEquals(emptyList<String>(), (blankOnly.items.last() as ChatItem.AgentMessage).memorySources)
    }

    @Test
    fun streamingAndFailedMessagesOmitMemorySources() {
        val memories = listOf(fact("m1", source = "task-0"))
        val streaming = AgentTask(
            taskId = "t",
            input = "我叫什么",
            status = TaskStatus.THINKING,
            streamingText = "你叫",
            retrievedMemories = memories,
        ).toChatUiState()
        assertEquals(emptyList<String>(), (streaming.items.last() as ChatItem.AgentMessage).memorySources)

        val failed = AgentTask(
            taskId = "t",
            input = "我叫什么",
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.EGRESS_BLOCKED,
            retrievedMemories = memories,
        ).toChatUiState()
        assertEquals(emptyList<String>(), (failed.items.last() as ChatItem.AgentMessage).memorySources)
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

    @Test
    fun screenAttachChipLabelHasSizeNotPixels() {
        assertEquals("已附上 · 720×1584", screenAttachChipLabel(720, 1584))
    }

    private fun fact(id: String, source: String) = MemoryEntry(
        id = id,
        content = "我叫小明，住在上海",
        source = source,
        confidence = 0.8f,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun ChatItem.kind(): String = when (this) {
        is ChatItem.UserMessage -> "user"
        is ChatItem.Thinking -> "thinking-$loopNumber"
        is ChatItem.ToolCard -> "tool-${entry.toolCallId}"
        is ChatItem.ConfirmCard -> "confirm-$toolCallId"
        is ChatItem.AgentMessage -> "agent"
    }
}
