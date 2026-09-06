package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.AttachmentKind

/** Shared Chat identity + task context. Do not log the assembled string. */
object ChatPromptAssembler {
    const val IDENTITY =
        "你是 Dougie，运行在用户手机上的本地优先助手。用中文回答。"

    fun systemPrefix(task: AgentTask): String {
        val parts = mutableListOf(IDENTITY)
        val lines = task.attachments.map { meta ->
            val kind = meta.kind.name.lowercase()
            val extra = if (meta.kind == AttachmentKind.SCREEN) {
                " Pixels stay on device; use screen_match on this capture_id. Do not expect image bytes."
            } else {
                " Photo metadata only unless cloud vision parts are present."
            }
            "attachment id=${meta.id} kind=$kind ${meta.width}x${meta.height}.$extra"
        }
        if (lines.isEmpty()) {
            val captureId = task.attachedCaptureId
            val width = task.attachedWidth
            val height = task.attachedHeight
            if (!captureId.isNullOrBlank() && width != null && height != null) {
                parts += "User attached screen capture_id=$captureId (${width}x$height). " +
                    "Use screen_match on this frame. Do not call screen_capture unless the user asks for a new capture."
            }
        } else {
            parts += "User attached:\n" + lines.joinToString("\n")
        }
        if (task.retrievedMemories.isNotEmpty()) {
            val facts = task.retrievedMemories.joinToString(separator = "\n") { "- ${it.content}" }
            parts += "Known facts:\n$facts"
        }
        return parts.joinToString("\n\n")
    }

    fun localPrompt(task: AgentTask): String {
        val traces = task.toolTrace.mapNotNull { trace ->
            val result = trace.resultJson ?: return@mapNotNull null
            "${trace.toolName}: $result"
        }
        val userBlock = if (traces.isEmpty()) {
            task.input
        } else {
            task.input + "\n" + traces.joinToString("\n")
        }
        return systemPrefix(task) + "\n\n" + userBlock
    }
}
