package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus

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
            if (tool.isNullOrBlank()) "待确认" else "待确认 · $tool"
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
