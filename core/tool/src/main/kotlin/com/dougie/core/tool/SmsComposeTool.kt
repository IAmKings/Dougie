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
    /**
     * Per-call tree observer for the verification layer. Channels without an observer
     * return null here, which keeps every result at verified=null. The snapshot never
     * leaves the tool call stack: verification yields only the verified boolean.
     */
    private val treeProbe: () -> TreeSnapshotPort? = { null },
    private val verifyTimeoutMs: Long = TreeVerifier.DEFAULT_TIMEOUT_MS,
    private val verifyPollMs: Long = TreeVerifier.DEFAULT_POLL_INTERVAL_MS,
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
        idempotencyStore.get(context.idempotencyKey)?.let {
            return ToolResult(json = it, verified = VerifiedResultJson.fromJson(it))
        }
        val json = port.composeSms(parsed.to, parsed.body)
        if (!isOk(json)) {
            return ToolResult(
                json = json,
                isFatal = true,
                error = UserFacingErrors.TELECOM_LAUNCH_FAILED,
            )
        }
        val probe = treeProbe()
        val verified = if (probe == null) {
            null
        } else {
            TreeVerifier.awaitContains(
                port = probe,
                needle = parsed.body,
                timeoutMs = verifyTimeoutMs,
                pollIntervalMs = verifyPollMs,
            )
        }
        if (verified == false) {
            // Verification failure is an observation for replan, not a fatal error — and it
            // must not enter the idempotency store so a retry really re-composes. The SMS
            // body never appears in any result JSON.
            return ToolResult(
                json = """{"ok":false,"error":"${UserFacingErrors.VERIFY_FAILED}","verified":false}""",
                isFatal = false,
                error = UserFacingErrors.VERIFY_FAILED,
                verified = false,
            )
        }
        val resultJson = if (verified == null) json else VerifiedResultJson.withFlag(json, verified)
        idempotencyStore.put(context.idempotencyKey, resultJson)
        val stored = idempotencyStore.get(context.idempotencyKey) ?: resultJson
        return ToolResult(json = stored, verified = verified)
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
