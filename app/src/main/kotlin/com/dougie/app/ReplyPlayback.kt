package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus

object ReplyPlayback {
    fun shouldSpeak(
        task: AgentTask?,
        previousStatus: TaskStatus?,
        spokenTaskId: String?,
    ): Boolean {
        val current = task ?: return false
        if (current.status != TaskStatus.COMPLETED) return false
        if (!current.speakReply) return false
        if (current.finalAnswer.isNullOrBlank()) return false
        if (current.taskId == spokenTaskId) return false
        if (previousStatus == null || previousStatus == TaskStatus.COMPLETED) return false
        return true
    }
}
