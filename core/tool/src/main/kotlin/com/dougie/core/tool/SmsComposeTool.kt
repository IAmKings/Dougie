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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SmsComposeTool(
    private val port: TelecomPort,
    private val idempotencyStore: IdempotencyStore = InMemoryIdempotencyStore(),
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override fun validateArguments(argumentsJson: String) {
        parseArgs(argumentsJson)
    }

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val parsed = parseArgs(argumentsJson)
        if (!port.isAppForeground()) {
            return ToolResult(
                json = failJson(UserFacingErrors.APP_INTENT_NOT_FOREGROUND),
                isFatal = true,
                error = UserFacingErrors.APP_INTENT_NOT_FOREGROUND,
            )
        }
        idempotencyStore.get(context.idempotencyKey)?.let { return ToolResult(json = it) }
        val json = port.composeSms(parsed.to, parsed.body)
        if (!isOk(json)) {
            return ToolResult(
                json = json,
                isFatal = true,
                error = UserFacingErrors.TELECOM_LAUNCH_FAILED,
            )
        }
        idempotencyStore.put(context.idempotencyKey, json)
        val stored = idempotencyStore.get(context.idempotencyKey) ?: json
        return ToolResult(json = stored)
    }

    private fun parseArgs(argumentsJson: String): Parsed {
        val obj = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val to = PhoneNumber.canonical(obj["to"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val body = obj["body"]?.jsonPrimitive?.contentOrNull
        if (body == null || body.trim().isEmpty() || body.length > MAX_BODY_UTF16) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        return Parsed(to = to, body = body)
    }

    private fun isOk(json: String): Boolean {
        return try {
            Json.parseToJsonElement(json).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull == true
        } catch (_: Exception) {
            false
        }
    }

    private fun failJson(message: String): String =
        """{"ok":false,"error":"$message"}"""

    private data class Parsed(val to: String, val body: String)

    companion object {
        const val NAME = "sms_compose"
        const val MAX_BODY_UTF16 = 1000
        val DESCRIPTOR = ToolDescriptor(
            name = NAME,
            description = "Open the system SMS composer with a recipient and body. Requires confirmation. Does not send.",
            properties = mapOf(
                "to" to ToolParamSpec(ToolParamType.STRING),
                "body" to ToolParamSpec(ToolParamType.STRING),
            ),
            riskLevel = RiskLevel.L3,
        )
    }
}
