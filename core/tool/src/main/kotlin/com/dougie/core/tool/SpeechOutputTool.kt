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

class SpeechOutputTool(
    private val port: PreferOfflineTtsPort,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override fun validateArguments(argumentsJson: String) {
        parseText(argumentsJson)
    }

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val text = parseText(argumentsJson)
        val spoken = port.speak(text)
        if (!spoken.ok) {
            val message = spoken.error ?: UserFacingErrors.TTS_FAILED
            return ToolResult(
                json = """{"ok":false,"backend":"${spoken.backend}","error":"$message"}""",
                isFatal = true,
                error = message,
            )
        }
        return ToolResult(json = """{"ok":true,"backend":"${spoken.backend}"}""")
    }

    private fun parseText(argumentsJson: String): String {
        val obj = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (text.isEmpty()) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        return text
    }

    companion object {
        const val NAME = "speech_output"
        val DESCRIPTOR = ToolDescriptor(
            name = NAME,
            description = "Speak short text aloud. Prefers on-device TTS; system TTS is fallback for short prompts only and never uses a network voice.",
            properties = mapOf(
                "text" to ToolParamSpec(ToolParamType.STRING),
            ),
            riskLevel = RiskLevel.L0,
        )
    }
}
