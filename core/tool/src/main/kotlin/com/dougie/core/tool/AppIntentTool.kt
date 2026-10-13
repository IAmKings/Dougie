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
    private val allowedPackages: () -> Set<String> = { emptySet() },
    /**
     * Per-call foreground observer for the verification layer. Channels without an observer
     * (e.g. Play, or accessibility not wired yet) return null here, which keeps every result
     * at verified=null instead of reporting unverifiable failures.
     */
    private val foregroundProbe: () -> ForegroundProbePort? = { null },
    private val verifyTimeoutMs: Long = ForegroundVerifier.DEFAULT_TIMEOUT_MS,
    private val verifyPollMs: Long = ForegroundVerifier.DEFAULT_POLL_INTERVAL_MS,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override fun validateArguments(argumentsJson: String) {
        val parsed = parseArgs(argumentsJson)
        AppIntentAllowlist.validate(parsed.uri, parsed.packageName, allowedPackages())
    }

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val parsed = parseArgs(argumentsJson)
        val canonical = AppIntentAllowlist.validate(parsed.uri, parsed.packageName, allowedPackages())
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
        val json = port.launchView(canonical, parsed.packageName)
        if (!isOk(json)) {
            return ToolResult(
                json = json,
                isFatal = true,
                error = UserFacingErrors.APP_INTENT_LAUNCH_FAILED,
            )
        }
        val expectedPackage = expectedPackageOf(canonical, parsed.packageName)
        val probe = if (expectedPackage == null) null else foregroundProbe()
        val verified = if (expectedPackage == null || probe == null) {
            null
        } else {
            ForegroundVerifier.awaitForeground(
                expectedPackage = expectedPackage,
                probe = probe,
                timeoutMs = verifyTimeoutMs,
                pollIntervalMs = verifyPollMs,
            )
        }
        if (verified == false) {
            // Verification failure is an observation for replan, not a fatal error — and it
            // must not enter the idempotency store so a retry really re-launches.
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

    private fun expectedPackageOf(canonical: String, packageName: String?): String? {
        packageName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        if (!canonical.startsWith("package:")) return null
        return canonical.removePrefix("package:").takeIf { it.isNotEmpty() }
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
            description = "Open an allowed http(s)/geo link or launch an installed app. Requires user confirmation. " +
                "For apps named in the user's openable-apps list, use their package: URI, never a web link. " +
                "Forbidden: tel, sms, file, javascript, content, intent.",
            properties = mapOf(
                "uri" to ToolParamSpec(ToolParamType.STRING),
                "package" to ToolParamSpec(ToolParamType.STRING, defaultJson = "\"\""),
            ),
            riskLevel = RiskLevel.L2,
        )
    }
}
