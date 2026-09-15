package com.dougie.feature.history

import com.dougie.core.model.AgentTask
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.conversationDisplayName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class HistoryToolStep(
    val toolCallId: String,
    val toolName: String,
    val statusLabel: String,
)

data class HistoryItem(
    val taskId: String,
    val conversationId: String,
    val inputSummary: String,
    val status: TaskStatus,
    val statusLabel: String,
    val loopCount: Int,
    val toolChain: String,
    val error: String?,
    val durationLabel: String? = null,
    val providerLabel: String? = null,
    val completedAtLabel: String? = null,
    val steps: List<HistoryToolStep> = emptyList(),
)

data class HistorySection(
    val conversationId: String,
    val title: String,
    val items: List<HistoryItem>,
)

fun toHistorySections(
    items: List<HistoryItem>,
    titles: Map<String, String> = emptyMap(),
): List<HistorySection> {
    if (items.isEmpty()) return emptyList()
    val numbered = LinkedHashMap<String, String>()
    var nextNumber = 2
    for (item in items.asReversed()) {
        val id = item.conversationId
        if (id == ConversationIds.DEFAULT || id in numbered) continue
        numbered[id] = "对话 $nextNumber"
        nextNumber++
    }
    val order = LinkedHashSet<String>()
    val grouped = LinkedHashMap<String, MutableList<HistoryItem>>()
    for (item in items) {
        order.add(item.conversationId)
        grouped.getOrPut(item.conversationId) { mutableListOf() }.add(item)
    }
    return order.map { id ->
        val numberedFallback = if (id == ConversationIds.DEFAULT) {
            "默认会话"
        } else {
            numbered[id] ?: "对话 2"
        }
        HistorySection(
            conversationId = id,
            title = conversationDisplayName(id, titles[id], numberedFallback),
            items = grouped.getValue(id),
        )
    }
}

fun currentConversationTitle(
    conversationId: String,
    titles: Map<String, String> = emptyMap(),
    recentItems: List<HistoryItem>,
    windowEmpty: Boolean,
): String {
    toHistorySections(recentItems, titles)
        .firstOrNull { it.conversationId == conversationId }
        ?.let { return it.title }
    return conversationDisplayName(
        conversationId = conversationId,
        customTitle = titles[conversationId],
        numberedFallback = "对话 2",
        isUnlistedNew = windowEmpty,
    )
}

fun AgentTask.toHistoryItem(
    maxInputChars: Int = 80,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): HistoryItem {
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
        durationLabel = formatTaskDuration(startedAt, endedAt),
        providerLabel = completionPath?.toUserLabel(),
        completedAtLabel = formatCompletedAt(endedAt, nowMs, zone),
        steps = toolTrace.map { it.toHistoryToolStep() },
    )
}

private fun ToolTraceEntry.toHistoryToolStep(): HistoryToolStep = HistoryToolStep(
    toolCallId = toolCallId,
    toolName = toolName,
    statusLabel = when (status) {
        ToolTraceStatus.SUCCESS -> "成功"
        ToolTraceStatus.FAILED -> "失败"
        else -> "进行中"
    },
)

fun formatTaskDuration(startedAt: Long?, endedAt: Long?): String? {
    if (startedAt == null || endedAt == null) return null
    val totalSeconds = (endedAt - startedAt).coerceAtLeast(0L) / 1000L
    if (totalSeconds < 1L) return "不足1秒"
    if (totalSeconds < 60L) return "${totalSeconds}秒"
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (seconds == 0L) "${minutes}分" else "${minutes}分${seconds}秒"
}

fun formatCompletedAt(
    endedAt: Long?,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    if (endedAt == null) return null
    val ended = Instant.ofEpochMilli(endedAt).atZone(zone)
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val days = ChronoUnit.DAYS.between(ended.toLocalDate(), now.toLocalDate())
    val clock = ended.format(COMPLETED_AT_CLOCK)
    return when {
        days == 0L -> "今天 $clock"
        days == 1L -> "昨天 $clock"
        ended.year == now.year -> "${ended.format(COMPLETED_AT_MONTH_DAY)} $clock"
        else -> "${ended.format(COMPLETED_AT_FULL_DATE)} $clock"
    }
}

private val COMPLETED_AT_CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val COMPLETED_AT_MONTH_DAY = DateTimeFormatter.ofPattern("M月d日", Locale.ROOT)
private val COMPLETED_AT_FULL_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.ROOT)

fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.COMPLETED -> "已完成"
    TaskStatus.FAILED -> "失败"
    else -> "已中断"
}
