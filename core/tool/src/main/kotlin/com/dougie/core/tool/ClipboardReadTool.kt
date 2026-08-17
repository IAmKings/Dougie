package com.dougie.core.tool

import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ClipboardReadTool(
    private val port: ClipboardPort,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = NAME,
        description = "Read clipboard text. Only works while the app is in the foreground.",
        riskLevel = RiskLevel.L1,
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        if (!port.isAppForeground()) {
            return ToolResult(
                json = buildJsonObject {
                    put("ok", false)
                    put("error", UserFacingErrors.CLIPBOARD_NOT_FOREGROUND)
                }.toString(),
                isFatal = true,
                error = UserFacingErrors.CLIPBOARD_NOT_FOREGROUND,
            )
        }
        val text = port.readText().orEmpty()
        return ToolResult(
            json = buildJsonObject {
                put("ok", true)
                put("text", JsonPrimitive(text))
            }.toString(),
        )
    }

    companion object {
        const val NAME = "clipboard_read"
    }
}
