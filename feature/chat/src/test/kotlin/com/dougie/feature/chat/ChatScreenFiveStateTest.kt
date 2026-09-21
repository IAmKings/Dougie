package com.dougie.feature.chat

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.dougie.core.model.AgentTask
import com.dougie.core.model.MemoryEntry
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-xhdpi")
class ChatScreenFiveStateTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun pauseInfiniteAnimations() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun userBubbleShowsVoiceCaptionOutsideBubbleText() {
        setChat(
            AgentTask(
                taskId = "t",
                input = TIME_EXAMPLE,
                status = TaskStatus.THINKING,
                speakReply = true,
            ).toChatUiState(),
        )
        compose.onNodeWithText(TIME_EXAMPLE, useUnmergedTree = true).assertTextEquals(TIME_EXAMPLE)
        compose.onNodeWithText("语音转写", useUnmergedTree = true).assertExists()
    }

    @Test
    fun thinkingShowsLoopCopyNotOrphanThinking() {
        setChat(
            AgentTask(
                taskId = "t",
                input = BATTERY_EXAMPLE,
                status = TaskStatus.THINKING,
            ).toChatUiState(),
        )
        compose.onNodeWithText("思考中… [循环 1]").assertExists()
        compose.onAllNodesWithText("正在思考", substring = true).assertCountEquals(0)
    }

    @Test
    fun toolCardShowsChineseNameExpandAndNoJsonDump() {
        setChat(
            AgentTask(
                taskId = "t",
                input = BATTERY_EXAMPLE,
                status = TaskStatus.COMPLETED,
                loopCount = 1,
                toolTrace = listOf(
                    ToolTraceEntry(
                        toolCallId = "battery-1",
                        toolName = "battery",
                        argsSummary = """{"secret_arg":"do-not-dump"}""",
                        resultJson = """{"battery_percent":63,"charging":true}""",
                        status = ToolTraceStatus.SUCCESS,
                    ),
                ),
                finalAnswer = "你现在的手机电量是 63%。",
            ).toChatUiState(),
        )
        compose.onNodeWithText("电池工具", substring = true).assertExists()
        compose.onNodeWithText("展开").assertExists()
        compose.onAllNodesWithText("battery_percent", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("secret_arg", substring = true).assertCountEquals(0)
    }

    @Test
    fun confirmOverlayShowsActionsAndIsNotAFeedRow() {
        setChat(
            AgentTask(
                taskId = "t",
                input = "帮我约明天下午开会",
                status = TaskStatus.AWAITING_CONFIRMATION,
                confirmDeadlineAt = 1_700_000_060_000L,
                toolTrace = listOf(
                    ToolTraceEntry(
                        toolCallId = "cal-1",
                        toolName = "calendar_create",
                        argsSummary = """{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}""",
                        status = ToolTraceStatus.PENDING,
                        riskLevel = RiskLevel.L2,
                    ),
                ),
            ).toChatUiState(),
        )
        compose.onNodeWithText("确认", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("拒绝", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("确认", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("拒绝", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("确认 创建日程", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun finalAnswerShowsTextAndMemorySource() {
        setChat(
            AgentTask(
                taskId = "t",
                input = "我叫什么",
                status = TaskStatus.COMPLETED,
                finalAnswer = "你叫小明。",
                retrievedMemories = listOf(fact("m1", source = "task-0")),
            ).toChatUiState(),
        )
        compose.onNodeWithText("你叫小明。").assertExists()
        compose.onNodeWithText("来源：task-0").assertExists()
    }

    @Test
    fun failedBubbleShowsTaskFailurePrefix() {
        setChat(
            AgentTask(
                taskId = "t",
                input = TIME_EXAMPLE,
                status = TaskStatus.FAILED,
                lastError = UserFacingErrors.LLM_EMPTY_REPLY,
            ).toChatUiState(),
        )
        compose.onNodeWithText("任务失败：", substring = true).assertExists()
    }

    private fun setChat(uiState: ChatUiState) {
        compose.setContent {
            ChatScreen(
                uiState = uiState,
                listState = rememberLazyListState(),
                onSend = {},
            )
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun fact(id: String, source: String) = MemoryEntry(
        id = id,
        content = "我叫小明，住在上海",
        source = source,
        confidence = 0.8f,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
