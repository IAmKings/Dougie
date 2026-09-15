package com.dougie.feature.history

import com.dougie.core.model.AgentTask
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.TaskStatus

data class HistoryItem(
    val taskId: String,
    val conversationId: String,
    val inputSummary: String,
    val status: TaskStatus,
    val statusLabel: String,
    val loopCount: Int,
    val toolChain: String,
    val error: String?,
)

data class HistorySection(
    val conversationId: String,
    val title: String,
    val items: List<HistoryItem>,
)

fun toHistorySections(items: List<HistoryItem>): List<HistorySection> {
    if (items.isEmpty()) return emptyList()
    val titles = LinkedHashMap<String, String>()
    var nextNumber = 2
    for (item in items.asReversed()) {
        val id = item.conversationId
        if (id == ConversationIds.DEFAULT || id in titles) continue
        titles[id] = "对话 $nextNumber"
        nextNumber++
    }
    val order = LinkedHashSet<String>()
    val grouped = LinkedHashMap<String, MutableList<HistoryItem>>()
    for (item in items) {
        order.add(item.conversationId)
        grouped.getOrPut(item.conversationId) { mutableListOf() }.add(item)
    }
    return order.map { id ->
        HistorySection(
            conversationId = id,
            title = if (id == ConversationIds.DEFAULT) "默认会话" else titles[id] ?: "对话 2",
            items = grouped.getValue(id),
        )
    }
}

fun AgentTask.toHistoryItem(maxInputChars: Int = 80): HistoryItem {
    val summary = if (input.length <= maxInputChars) input else input.take(maxInputChars) + "…"
    return HistoryItem(
        taskId = taskId,
        conversationId = conversationId,
        inputSummary = summary,
        status = status,
        statusLabel = statusLabel(status),
        loopCount = loopCount,
        toolChain = toolTrace.joinToString(" → ") { it.toolName },
        error = lastError.takeIf { status == TaskStatus.FAILED },
    )
}

fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.COMPLETED -> "已完成"
    TaskStatus.FAILED -> "失败"
    else -> "已中断"
}
