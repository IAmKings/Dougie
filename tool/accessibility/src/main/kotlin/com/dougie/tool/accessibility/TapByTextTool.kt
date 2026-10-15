package com.dougie.tool.accessibility

import com.dougie.core.model.AgentException
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamSpec
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.ForegroundProbePort
import com.dougie.core.tool.ForegroundVerifier
import com.dougie.core.tool.IdempotencyStore
import com.dougie.core.tool.InMemoryIdempotencyStore
import com.dougie.core.tool.TapSourcePort
import com.dougie.core.tool.TapTargetSelector
import com.dougie.core.tool.TreeSnapshotPort
import com.dougie.core.tool.TreeVerifier
import com.dougie.core.tool.VerifiedResultJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tap a control by its text description: the accessibility tree locates the node
 * deterministically, then a real gesture is dispatched at its bounds center. One-sided
 * verification: an observable state fingerprint change confirms verified=true; no change
 * stays verified=null (honest unknown, never a failure — pixel-only effects are blind here).
 * A miss (no matching text) is a non-fatal observation so the model can rephrase.
 */
class TapByTextTool(
    private val consentGranted: () -> Boolean,
    private val port: GesturePort,
    private val tapSource: TapSourcePort,
    private val treeProbe: () -> TreeSnapshotPort? = { null },
    private val idempotencyStore: IdempotencyStore = InMemoryIdempotencyStore(),
    private val verifyTimeoutMs: Long = TreeVerifier.DEFAULT_STATE_CHANGE_TIMEOUT_MS,
    private val verifyPollMs: Long = TreeVerifier.DEFAULT_POLL_INTERVAL_MS,
    private val selfPackage: String? = null,
    private val selfForegroundGraceMs: Long = ForegroundVerifier.SELF_FOREGROUND_GRACE_MS,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override fun validateArguments(argumentsJson: String) {
        parseArgs(argumentsJson)
    }

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val needle = parseArgs(argumentsJson)
        if (!consentGranted()) {
            return fail(TapSwipeTool.CONSENT_REQUIRED)
        }
        if (!port.isConnected()) {
            return fail(TapSwipeTool.SERVICE_REQUIRED)
        }
        var foreground = port.foregroundPackage()?.trim().orEmpty()
        // Same self-foreground guard as tap_swipe: searching Dougie's own tree (its chat
        // contains the needle text) would tap inside Dougie instead of the target app.
        // The confirm may have been tapped from the overlay while Dougie is front — hold
        // a grace window for the user to walk back to the target app, then proceed. If
        // they never leave, a NON-FATAL observation goes back to the model (replan, e.g.
        // re-open the app); a fumble must not kill the whole chain.
        if (selfPackage != null && foreground == selfPackage) {
            val returned = ForegroundVerifier.awaitNotForeground(
                blockedPackage = selfPackage,
                probe = ForegroundProbePort { port.foregroundPackage() },
                timeoutMs = selfForegroundGraceMs,
            )
            foreground = port.foregroundPackage()?.trim().orEmpty()
            if (!returned || foreground == selfPackage) {
                return selfForeground()
            }
        }
        if (foreground.isEmpty()) {
            return fail(TapSwipeTool.FOREGROUND_UNKNOWN)
        }
        if (HighRiskForeground.isBlocked(foreground)) {
            return fail(TapSwipeTool.BLOCKED_APP)
        }
        idempotencyStore.get(context.idempotencyKey)?.let {
            return ToolResult(json = it, verified = VerifiedResultJson.fromJson(it))
        }
        val beforeSnapshot = treeProbe()?.snapshot()
        val target = TapTargetSelector.select(tapSource.candidates(), needle)
            ?: return notFound(needle)
        if (!port.tap(target.centerX, target.centerY)) {
            return fail(UserFacingErrors.TOOL_FAILED)
        }
        val verified = if (beforeSnapshot != null) {
            val probe = treeProbe()
            if (probe != null &&
                TreeVerifier.awaitStateChange(
                    before = beforeSnapshot,
                    port = probe,
                    timeoutMs = verifyTimeoutMs,
                    pollIntervalMs = verifyPollMs,
                )
            ) {
                true
            } else {
                null
            }
        } else {
            null
        }
        val raw = successJson(needle)
        val json = if (verified == null) raw else VerifiedResultJson.withFlag(raw, verified)
        idempotencyStore.put(context.idempotencyKey, json)
        val stored = idempotencyStore.get(context.idempotencyKey) ?: json
        return ToolResult(json = stored, verified = verified)
    }

    private fun parseArgs(argumentsJson: String): String {
        val obj = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_TEXT) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        return text
    }

    private fun notFound(needle: String): ToolResult = ToolResult(
        json = """{"ok":false,"found":false,"text":"$needle","error":"${UserFacingErrors.TAP_TEXT_NOT_FOUND}"}""",
        isFatal = false,
        error = UserFacingErrors.TAP_TEXT_NOT_FOUND,
    )

    /** Non-fatal: nothing was tapped; the model gets the observation and can replan. */
    private fun selfForeground(): ToolResult = ToolResult(
        json = """{"ok":false,"selfForeground":true,"error":"${TapSwipeTool.SELF_FOREGROUND}"}""",
        isFatal = false,
        error = TapSwipeTool.SELF_FOREGROUND,
    )

    private fun fail(message: String): ToolResult =
        ToolResult(json = """{"ok":false,"error":"$message"}""", isFatal = true, error = message)

    private fun successJson(needle: String): String =
        """{"ok":true,"action":"tap_by_text","text":"$needle"}"""

    companion object {
        const val NAME = "tap_by_text"
        const val MAX_TEXT = 50
        val DESCRIPTOR = ToolDescriptor(
            name = NAME,
            description = "Tap a control by its visible text description (finds the node via accessibility, " +
                "then taps its screen position). Sideload only. Always needs confirmation. " +
                "Use for taps on named controls instead of guessing coordinates. " +
                "Forbidden on banks, payments, and password managers.",
            properties = mapOf(
                "text" to ToolParamSpec(ToolParamType.STRING),
            ),
            riskLevel = RiskLevel.L3,
        )
    }
}
