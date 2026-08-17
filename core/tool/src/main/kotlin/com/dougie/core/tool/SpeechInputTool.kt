package com.dougie.core.tool

import com.dougie.core.model.AndroidPermissions
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SpeechInputTool(
    private val port: SpeechPort,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = NAME,
        description = "Capture one foreground voice utterance and return local transcript text only. Never sends audio off-device.",
        riskLevel = RiskLevel.L1,
        androidPermission = AndroidPermissions.RECORD_AUDIO,
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        if (!port.isAppForeground()) {
            return fail(UserFacingErrors.SPEECH_NOT_FOREGROUND)
        }
        if (!port.isModelPresent()) {
            return fail(UserFacingErrors.SPEECH_MODEL_MISSING)
        }
        if (!port.isEngineReady()) {
            return fail(UserFacingErrors.SPEECH_ENGINE_NOT_READY)
        }
        val text = port.listen()
        if (text.isBlank()) {
            return fail(UserFacingErrors.SPEECH_EMPTY)
        }
        return ToolResult(
            json = buildJsonObject {
                put("ok", true)
                put("text", JsonPrimitive(text))
            }.toString(),
        )
    }

    private fun fail(message: String): ToolResult =
        ToolResult(
            json = buildJsonObject {
                put("ok", false)
                put("error", message)
            }.toString(),
            isFatal = true,
            error = message,
        )

    companion object {
        const val NAME = "speech_input"
    }
}
