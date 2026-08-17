package com.dougie.feature.history

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus

data class HistoryItem(
    val taskId: String,
    val inputSummary: String,
    val status: TaskStatus,
    val statusLabel: String,
    val loopCount: Int,
    val toolChain: String,
    val error: String?,
)

fun AgentTask.toHistoryItem(maxInputChars: Int = 80): HistoryItem {
    val summary = if (input.length <= maxInputChars) input else input.take(maxInputChars) + "…"
    return HistoryItem(
        taskId = taskId,
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
