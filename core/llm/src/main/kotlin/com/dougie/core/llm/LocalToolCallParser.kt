package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.ToolTraceStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Whole-reply JSON → [LlmEvent.ToolCall], including one markdown fence.
 * Mixed Chinese plus JSON stays text. Unknown names still parse; Loop sanitizer rejects them.
 * Do not log the reply.
 */
object LocalToolCallParser {
    fun parse(reply: String): LlmEvent.ToolCall? {
        val text = unwrapMarkdownFence(reply.trim())
        if (text.isEmpty() || !text.startsWith("{") || !text.endsWith("}")) {
            return null
        }
        val obj = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return null
        }
        val name = obj["name"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim().orEmpty()
        if (name.isEmpty()) {
            return null
        }
        return LlmEvent.ToolCall(
            id = "",
            name = name,
            argsJson = argsJson(obj["args"]),
        )
    }

    fun isRepeatOfSuccessfulCall(task: AgentTask, call: LlmEvent.ToolCall): Boolean {
        val args = call.argsJson.trim()
        return task.toolTrace.any { trace ->
            trace.status == ToolTraceStatus.SUCCESS &&
                trace.toolName == call.name &&
                trace.argsSummary.trim() == args
        }
    }

    private fun unwrapMarkdownFence(text: String): String {
        if (!text.startsWith("```")) return text
        val withoutOpen = text.removePrefix("```").let { body ->
            val stripped = if (body.startsWith("json", ignoreCase = true)) {
                body.substring(4)
            } else {
                body
            }
            stripped.trimStart('\n', '\r', ' ')
        }
        if (!withoutOpen.endsWith("```")) return text
        return withoutOpen.removeSuffix("```").trim()
    }

    private fun argsJson(element: JsonElement?): String {
        return when (element) {
            null, JsonNull -> "{}"
            is JsonObject -> element.toString()
            is JsonPrimitive -> {
                val raw = element.contentOrNull?.trim().orEmpty()
                if (raw.isEmpty()) "{}" else raw
            }
            else -> element.toString()
        }
    }
}
