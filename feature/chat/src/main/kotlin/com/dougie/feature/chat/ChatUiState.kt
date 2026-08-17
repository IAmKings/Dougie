package com.dougie.feature.chat

import com.dougie.core.model.AgentTask
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry

const val BATTERY_EXAMPLE = "我现在手机还有多少电？"
const val TIME_EXAMPLE = "现在几点了？"

data class ChatUiState(
    val items: List<ChatItem> = emptyList(),
    val inputEnabled: Boolean = true,
    val isEmpty: Boolean = true,
    val canRetry: Boolean = false,
)

sealed class ChatItem {
    data class UserMessage(val text: String) : ChatItem()
    data class Thinking(val loopNumber: Int) : ChatItem()
    data class ToolCard(val entry: ToolTraceEntry) : ChatItem()
    data class ConfirmCard(
        val toolName: String,
        val argsJson: String,
        val riskLevel: RiskLevel,
        val toolCallId: String,
    ) : ChatItem()
    data class AgentMessage(val text: String) : ChatItem()
}

fun AgentTask?.toChatUiState(): ChatUiState {
    if (this == null) {
        return ChatUiState(isEmpty = true, inputEnabled = true)
    }
    val items = buildList {
        add(ChatItem.UserMessage(input))
        toolTrace.forEachIndexed { index, entry ->
            add(ChatItem.Thinking(loopNumber = index + 1))
            val awaitingThis = status == TaskStatus.AWAITING_CONFIRMATION && index == toolTrace.lastIndex
            if (awaitingThis) {
                add(
                    ChatItem.ConfirmCard(
                        toolName = entry.toolName,
                        argsJson = entry.argsSummary,
                        riskLevel = entry.riskLevel,
                        toolCallId = entry.toolCallId,
                    ),
                )
            } else {
                add(ChatItem.ToolCard(entry))
            }
        }
        val nextLoop = loopCount + 1
        val alreadyShowingNextThinking = toolTrace.size >= nextLoop
        if ((status == TaskStatus.PREPARING || status == TaskStatus.THINKING) && !alreadyShowingNextThinking) {
            add(ChatItem.Thinking(loopNumber = nextLoop))
        }
        val streaming = streamingText
        if (!streaming.isNullOrBlank() && status != TaskStatus.COMPLETED && status != TaskStatus.FAILED) {
            add(ChatItem.AgentMessage(streaming))
        }
        val answer = finalAnswer
        if (status == TaskStatus.COMPLETED && !answer.isNullOrBlank()) {
            add(ChatItem.AgentMessage(answer))
        }
        val error = lastError
        if (status == TaskStatus.FAILED && !error.isNullOrBlank()) {
            add(ChatItem.AgentMessage("任务失败：$error"))
        }
    }
    val busy = status != TaskStatus.COMPLETED && status != TaskStatus.FAILED && status != TaskStatus.IDLE
    return ChatUiState(
        items = items,
        inputEnabled = !busy,
        isEmpty = false,
        canRetry = status == TaskStatus.FAILED,
    )
}
