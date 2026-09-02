package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.model.CompletionPath
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceStatus

object ScreenShortcutHint {
    fun shouldShow(task: AgentTask?): Boolean {
        if (task == null) return false
        if (task.status != TaskStatus.COMPLETED) return false
        if (task.completionPath != CompletionPath.LOCAL_INTENT) return false
        val last = task.toolTrace.lastOrNull() ?: return false
        return last.toolName == "screen_capture" && last.status == ToolTraceStatus.SUCCESS
    }
}
