package com.dougie.app

import android.app.PendingIntent
import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun formatTaskNotice(task: AgentTask?): String? {
    if (task == null || task.status == TaskStatus.IDLE) return null
    val n = task.loopCount
    val tool = task.toolTrace.lastOrNull()?.toolName
    return when (task.status) {
        TaskStatus.PREPARING, TaskStatus.THINKING -> "思考中 · 循环 $n"
        TaskStatus.TOOL_PENDING, TaskStatus.TOOL_EXECUTING, TaskStatus.TOOL_RESULT -> {
            if (tool.isNullOrBlank()) "工具 · 循环 $n" else "工具 · 循环 $n · $tool"
        }
        TaskStatus.AWAITING_CONFIRMATION -> {
            val detail = confirmSummary(task)
            if (detail != null) "待确认 · $detail" else {
                if (tool.isNullOrBlank()) "待确认" else "待确认 · $tool"
            }
        }
        TaskStatus.COMPLETED -> "已完成 · 循环 $n"
        TaskStatus.FAILED -> "任务失败 · 循环 $n"
        TaskStatus.IDLE -> null
    }
}

fun isTaskBusy(task: AgentTask?): Boolean {
    val status = task?.status ?: return false
    return status != TaskStatus.IDLE &&
        status != TaskStatus.COMPLETED &&
        status != TaskStatus.FAILED
}

/** Bubble intents must be mutable (NMS: "PendingIntents attached to bubbles must be mutable"). */
fun taskNoticeBubblePendingFlags(sdkInt: Int): Int {
    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (sdkInt >= 31) {
        flags = flags or PendingIntent.FLAG_MUTABLE
    }
    return flags
}

/**
 * Human-readable summary of what the pending confirmation will do — e.g. 点击“记录” for
 * tap_by_text — so every confirm surface (card, notification, overlay) shows the standard
 * instead of a bare tool name. Null when the tool carries no describable payload.
 */
fun confirmSummary(task: AgentTask): String? {
    if (task.status != TaskStatus.AWAITING_CONFIRMATION) return null
    val last = task.toolTrace.lastOrNull() ?: return null
    if (last.status != ToolTraceStatus.PENDING) return null
    val text = try {
        Json.parseToJsonElement(last.argsSummary).jsonObject["text"]
            ?.jsonPrimitive?.contentOrNull?.trim()
    } catch (_: Exception) {
        null
    } ?: return null
    if (text.isEmpty()) return null
    return when (last.toolName) {
        "tap_by_text" -> "点击“$text”"
        else -> null
    }
}
