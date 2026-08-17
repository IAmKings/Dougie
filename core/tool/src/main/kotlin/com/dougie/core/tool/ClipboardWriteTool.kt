package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamSpec
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClipboardWriteTool(
    private val port: ClipboardPort,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = NAME,
        description = "Write text to the clipboard. Requires user confirmation.",
        properties = mapOf(
            "text" to ToolParamSpec(ToolParamType.STRING),
        ),
        riskLevel = RiskLevel.L2,
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val text = parseText(argumentsJson)
        port.writeText(text)
        return ToolResult(json = """{"ok":true}""")
    }

    private fun parseText(argumentsJson: String): String {
        val obj = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val text = obj["text"]?.jsonPrimitive?.contentOrNull
        if (text == null) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        return text
    }

    companion object {
        const val NAME = "clipboard_write"
    }
}
