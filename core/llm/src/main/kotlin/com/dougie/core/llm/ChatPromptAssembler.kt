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

    fun looksLikeLocalToolAsk(input: String): Boolean =
        LOCAL_TOOL_ASK_NEEDLES.any { needle -> input.contains(needle) }

    fun looksLikeLocalIdentityAsk(input: String): Boolean =
        LOCAL_IDENTITY_ASK_NEEDLES.any { needle -> input.contains(needle) }

    fun localToolProtocolActive(
        task: AgentTask,
        descriptors: List<ToolDescriptor>,
    ): Boolean {
        if (localTeachable(descriptors).isEmpty()) return false
        val hasResult = task.toolTrace.any { it.resultJson != null }
        return !hasResult && looksLikeLocalToolAsk(task.input)
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
        val protocolActive = localToolProtocolActive(task, descriptors)
        val prefix = systemPrefix(task, if (protocolActive) taught else emptyList())
        val followUp = when {
            protocolActive -> prefix + "\n\n" + localToolProtocol(taught)
            traces.isNotEmpty() && taught.isNotEmpty() -> prefix + "\n\n" + LOCAL_AFTER_TOOL_RESULTS
            traces.isNotEmpty() -> prefix
            else -> prefix + "\n\n" + LOCAL_IDLE_SUFFIX
        }
        val body = followUp + "\n\n" + userBlock
        return if (
            !protocolActive &&
            traces.isEmpty() &&
            looksLikeLocalIdentityAsk(task.input)
        ) {
            body + "\n\n" + LOCAL_IDENTITY_LOCK
        } else {
            body
        }
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
                "clipboard_write" -> "写剪贴板"
                "calendar_create" -> "建日历"
                "app_intent" -> "打开应用"
                "screen_match" -> "模板匹配"
                "speech_output" -> "念出来"
                else -> descriptor.name
            }
            "$label {\"name\":\"${descriptor.name}\",\"args\":${exampleArgs(descriptor.name)}}"
        }
        return "若需要工具，整段回复必须是一行 JSON。" +
            examples +
            "。同一工具不要连续调用。得到结果后必须用中文回答用户。" +
            "建日历仅当用户给了日期或钟点；startIso 按用户说的时间改写，不要照抄示例。" +
            "打开应用只用 package:包名，不要把应用中文名或 intent 当 uri。" +
            "用户说念出来、读出来或播报时只用念出来 JSON，不要建日历。"
    }

    private fun exampleArgs(name: String): String = when (name) {
        "clipboard_write" -> "{\"text\":\"示例文字\"}"
        "calendar_create" -> "{\"title\":\"开会\",\"startIso\":\"2026-09-08T15:00:00+08:00\"}"
        "app_intent" -> "{\"uri\":\"package:com.example.app\"}"
        "screen_match" -> "{\"template_id\":\"solid\"}"
        "speech_output" -> "{\"text\":\"要念的原文\"}"
        else -> "{}"
    }

    private val LOCAL_TEACH_NAMES = listOf(
        "time",
        "battery",
        "clipboard_read",
        "location",
        "calendar_query",
        "screen_capture",
        "speech_input",
        "clipboard_write",
        "calendar_create",
        "app_intent",
        "screen_match",
        "speech_output",
    )

    private val LOCAL_TOOL_ASK_NEEDLES = listOf(
        "几点",
        "电量",
        "剪贴板",
        "打开",
        "念出来",
        "日历",
        "定位",
        "截屏",
        "电池",
        "读出来",
        "播报",
        "日程",
        "截图",
    )

    private val LOCAL_IDENTITY_ASK_NEEDLES = listOf(
        "你是谁",
        "你是什么",
        "什么模型",
        "哪个模型",
        "你叫什么",
    )

    private const val LOCAL_AFTER_TOOL_RESULTS =
        "下面已有工具结果。用一两句中文直接回答，不要复述用户的问题，不要再输出工具 JSON。"

    private const val LOCAL_IDLE_SUFFIX =
        "直接用一两句中文回答。不要输出 JSON。不要罗列工具。不要自称其它模型或厂商。"

    /** After the user turn so MiniCPM does not answer OpenBMB. Do not name vendors here. */
    private const val LOCAL_IDENTITY_LOCK =
        "只回答：我是 Dougie，运行在用户手机上的本地优先助手。"
}
