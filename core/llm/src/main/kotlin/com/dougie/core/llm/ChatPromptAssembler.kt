package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.ToolDescriptor

/** Shared Chat identity + task context. Do not log the assembled string. */
object ChatPromptAssembler {
    const val IDENTITY =
        "你是 Dougie，运行在用户手机上的本地优先助手。用中文回答。"

    fun systemPrefix(
        task: AgentTask,
        descriptors: List<ToolDescriptor> = emptyList(),
    ): String {
        val parts = mutableListOf(IDENTITY)
        if (descriptors.isNotEmpty()) {
            parts += toolsInventory(descriptors)
        }
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

    fun localTeachable(descriptors: List<ToolDescriptor>): List<ToolDescriptor> {
        val byName = descriptors.associateBy { it.name }
        return LOCAL_TEACH_NAMES.mapNotNull { byName[it] }
    }

    fun localPrompt(
        task: AgentTask,
        descriptors: List<ToolDescriptor> = emptyList(),
    ): String {
        val taught = localTeachable(descriptors)
        val traces = task.toolTrace.mapNotNull { trace ->
            val result = trace.resultJson ?: return@mapNotNull null
            "${trace.toolName}: $result"
        }
        val userBlock = when {
            traces.isEmpty() -> task.input
            descriptors.isNotEmpty() -> traces.joinToString("\n")
            else -> task.input + "\n" + traces.joinToString("\n")
        }
        val prefix = systemPrefix(task, taught)
        val followUp = when {
            taught.isEmpty() -> prefix
            traces.isNotEmpty() -> prefix + "\n\n" + LOCAL_AFTER_TOOL_RESULTS
            else -> prefix + "\n\n" + localToolProtocol(taught)
        }
        return followUp + "\n\n" + userBlock
    }

    fun stripLeadingQuestion(reply: String, question: String): String {
        val raw = reply.trim()
        val q = question.trim()
        if (raw.isEmpty() || q.isEmpty()) return raw
        val variants = linkedSetOf(
            q,
            q.trimEnd('？', '?', '。', '！', '!'),
            "$q？",
            "$q?",
        ).filter { it.isNotEmpty() }.sortedByDescending { it.length }
        for (prefix in variants) {
            if (!raw.startsWith(prefix)) continue
            val rest = raw.removePrefix(prefix).trimStart('？', '?', ' ', '\n', '\r', '，', ',', '。', ':', '：')
            if (rest.isNotEmpty()) return rest
        }
        return raw
    }

    private fun toolsInventory(descriptors: List<ToolDescriptor>): String {
        val lines = descriptors.joinToString("\n") { descriptor ->
            val description = descriptor.description.ifBlank { descriptor.name }
            "- ${descriptor.name}: $description"
        }
        return "可用工具:\n$lines"
    }

    private fun localToolProtocol(taught: List<ToolDescriptor>): String {
        val examples = taught.joinToString("。") { descriptor ->
            val label = when (descriptor.name) {
                "time" -> "时间"
                "battery" -> "电量"
                "clipboard_read" -> "剪贴板"
                "location" -> "定位"
                "calendar_query" -> "日历查询"
                "screen_capture" -> "截屏"
                "speech_input" -> "语音输入"
                else -> descriptor.name
            }
            "$label {\"name\":\"${descriptor.name}\",\"args\":{}}"
        }
        return "若需要工具，整段回复必须是一行 JSON。" +
            examples +
            "。同一工具不要连续调用。得到结果后必须用中文回答用户。"
    }

    private val LOCAL_TEACH_NAMES = listOf(
        "time",
        "battery",
        "clipboard_read",
        "location",
        "calendar_query",
        "screen_capture",
        "speech_input",
    )

    private const val LOCAL_AFTER_TOOL_RESULTS =
        "下面已有工具结果。用一两句中文直接回答，不要复述用户的问题，不要再输出工具 JSON。"
}
