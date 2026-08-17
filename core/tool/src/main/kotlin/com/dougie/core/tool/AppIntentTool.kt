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

class AppIntentTool(
    private val port: AppIntentPort,
    private val idempotencyStore: IdempotencyStore = InMemoryIdempotencyStore(),
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override fun validateArguments(argumentsJson: String) {
        val parsed = parseArgs(argumentsJson)
        AppIntentAllowlist.validate(parsed.uri, parsed.packageName)
    }

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val parsed = parseArgs(argumentsJson)
        val canonical = AppIntentAllowlist.validate(parsed.uri, parsed.packageName)
        if (!port.isAppForeground()) {
            return ToolResult(
                json = failJson(UserFacingErrors.APP_INTENT_NOT_FOREGROUND),
                isFatal = true,
                error = UserFacingErrors.APP_INTENT_NOT_FOREGROUND,
            )
        }
        idempotencyStore.get(context.idempotencyKey)?.let { return ToolResult(json = it) }
        val json = port.launchView(canonical, parsed.packageName)
        if (!isOk(json)) {
            return ToolResult(
                json = json,
                isFatal = true,
                error = UserFacingErrors.APP_INTENT_LAUNCH_FAILED,
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
        val uri = obj["uri"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (uri.isEmpty()) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val packageName = obj["package"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        return Parsed(uri = uri, packageName = packageName)
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

    private data class Parsed(val uri: String, val packageName: String?)

    companion object {
        const val NAME = "app_intent"
        val DESCRIPTOR = ToolDescriptor(
            name = NAME,
            description = "Open an allowed http(s)/geo link or launch an installed app. Requires user confirmation. Forbidden: tel, sms, file, javascript, content, intent.",
            properties = mapOf(
                "uri" to ToolParamSpec(ToolParamType.STRING),
                "package" to ToolParamSpec(ToolParamType.STRING, defaultJson = "\"\""),
            ),
            riskLevel = RiskLevel.L2,
        )
    }
}
