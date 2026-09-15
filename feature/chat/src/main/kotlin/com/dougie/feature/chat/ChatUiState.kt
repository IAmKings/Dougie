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
    val canSpeakReply: Boolean = false,
    val canNewConversation: Boolean = false,
)

sealed class ChatItem {
    abstract val listKey: String

    data class UserMessage(
        val text: String,
        override val listKey: String,
    ) : ChatItem()

    data class Thinking(
        val loopNumber: Int,
        val live: Boolean = false,
        override val listKey: String,
    ) : ChatItem()

    data class ToolCard(
        val entry: ToolTraceEntry,
        override val listKey: String,
    ) : ChatItem()

    data class ConfirmCard(
        val toolName: String,
        val argsJson: String,
        val riskLevel: RiskLevel,
        val toolCallId: String,
        override val listKey: String,
    ) : ChatItem()

    data class AgentMessage(
        val text: String,
        val memorySources: List<String> = emptyList(),
        override val listKey: String,
    ) : ChatItem()
}

fun AgentTask?.toChatUiState(): ChatUiState {
    if (this == null) {
        return ChatUiState(isEmpty = true, inputEnabled = true)
    }
    val items = buildList {
        add(ChatItem.UserMessage(input, listKey = itemKey("user")))
        toolTrace.forEachIndexed { index, entry ->
            val loop = index + 1
            add(ChatItem.Thinking(loopNumber = loop, live = false, listKey = itemKey("thinking-$loop")))
            val awaitingThis = status == TaskStatus.AWAITING_CONFIRMATION && index == toolTrace.lastIndex
            if (awaitingThis) {
                add(
                    ChatItem.ConfirmCard(
                        toolName = entry.toolName,
                        argsJson = entry.argsSummary,
                        riskLevel = entry.riskLevel,
                        toolCallId = entry.toolCallId,
                        listKey = itemKey("confirm-${entry.toolCallId}"),
                    ),
                )
            } else {
                add(ChatItem.ToolCard(entry, listKey = itemKey("tool-${entry.toolCallId}")))
            }
        }
        val nextLoop = loopCount + 1
        val alreadyShowingNextThinking = toolTrace.size >= nextLoop
        if ((status == TaskStatus.PREPARING || status == TaskStatus.THINKING) && !alreadyShowingNextThinking) {
            add(ChatItem.Thinking(loopNumber = nextLoop, live = true, listKey = itemKey("thinking-$nextLoop")))
        }
        val streaming = streamingText
        if (!streaming.isNullOrBlank() && status != TaskStatus.COMPLETED && status != TaskStatus.FAILED) {
            add(ChatItem.AgentMessage(streaming, listKey = itemKey("agent")))
        }
        val answer = finalAnswer
        if (status == TaskStatus.COMPLETED && !answer.isNullOrBlank()) {
            add(ChatItem.AgentMessage(answer, memorySources = citationSources(), listKey = itemKey("agent")))
        }
        val error = lastError
        if (status == TaskStatus.FAILED && !error.isNullOrBlank()) {
            add(ChatItem.AgentMessage("任务失败：$error", listKey = itemKey("agent")))
        }
    }
    val busy = status != TaskStatus.COMPLETED && status != TaskStatus.FAILED && status != TaskStatus.IDLE
    return ChatUiState(
        items = items,
        inputEnabled = !busy,
        isEmpty = false,
        canRetry = status == TaskStatus.FAILED,
        canSpeakReply = status == TaskStatus.COMPLETED,
        canNewConversation = !busy,
    )
}

fun mergeChatUiState(live: AgentTask?, past: List<AgentTask>): ChatUiState {
    val liveState = live.toChatUiState()
    val liveId = live?.taskId
    val pastItems = past.filter { it.taskId != liveId }.flatMap { it.toPastChatItems() }
    val empty = pastItems.isEmpty() && liveState.isEmpty
    return liveState.copy(
        items = pastItems + liveState.items,
        isEmpty = empty,
        canNewConversation = !empty && liveState.inputEnabled,
    )
}

fun shouldFollowChatFeed(
    itemCount: Int,
    firstKey: String?,
    lastAgent: String?,
    previousItemCount: Int,
    previousFirstKey: String?,
    previousLastAgent: String?,
    pendingFocusKey: String? = null,
): Boolean {
    if (itemCount <= 0) return false
    if (!pendingFocusKey.isNullOrEmpty()) return false
    if (previousItemCount <= 0) return true
    if (firstKey != previousFirstKey) return true
    if (itemCount > previousItemCount) return true
    return lastAgent != previousLastAgent
}

fun userMessageListKey(taskId: String): String = "$taskId:user"

fun AgentTask.toPastChatItems(): List<ChatItem> {
    val items = ArrayList<ChatItem>(2)
    items.add(ChatItem.UserMessage(input, listKey = itemKey("user")))
    val answer = finalAnswer
    if (status == TaskStatus.COMPLETED && !answer.isNullOrBlank()) {
        items.add(ChatItem.AgentMessage(answer, memorySources = citationSources(), listKey = itemKey("agent")))
    }
    val error = lastError
    if (status == TaskStatus.FAILED && !error.isNullOrBlank()) {
        items.add(ChatItem.AgentMessage("任务失败：$error", listKey = itemKey("agent")))
    }
    return items
}

internal fun AgentTask.itemKey(part: String): String = "$taskId:$part"

internal fun AgentTask.citationSources(): List<String> {
    val seen = LinkedHashSet<String>()
    for (entry in retrievedMemories) {
        val source = entry.source.trim()
        if (source.isNotEmpty()) {
            seen.add(source)
        }
    }
    return seen.toList()
}
