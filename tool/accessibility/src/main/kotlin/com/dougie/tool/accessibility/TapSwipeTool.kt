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
import com.dougie.core.tool.IdempotencyStore
import com.dougie.core.tool.InMemoryIdempotencyStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TapSwipeTool(
    private val consentGranted: () -> Boolean,
    private val port: GesturePort,
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
        if (!consentGranted()) {
            return fail(CONSENT_REQUIRED)
        }
        if (!port.isConnected()) {
            return fail(SERVICE_REQUIRED)
        }
        if (HighRiskForeground.isBlocked(port.foregroundPackage())) {
            return fail(BLOCKED_APP)
        }
        idempotencyStore.get(context.idempotencyKey)?.let { return ToolResult(json = it) }
        val dispatched = when (parsed.action) {
            Action.TAP -> port.tap(parsed.x, parsed.y)
            Action.SWIPE -> port.swipe(parsed.x, parsed.y, parsed.x2, parsed.y2, parsed.durationMs)
        }
        if (!dispatched) {
            return fail(UserFacingErrors.TOOL_FAILED)
        }
        val json = successJson(parsed)
        idempotencyStore.put(context.idempotencyKey, json)
        return ToolResult(json = idempotencyStore.get(context.idempotencyKey) ?: json)
    }

    private fun parseArgs(argumentsJson: String): Parsed {
        val obj = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val action = when (obj["action"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()) {
            "tap" -> Action.TAP
            "swipe" -> Action.SWIPE
            else -> throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val x = nonNegative(obj["x"]?.jsonPrimitive?.intOrNull)
        val y = nonNegative(obj["y"]?.jsonPrimitive?.intOrNull)
        val durationMs = (obj["durationMs"]?.jsonPrimitive?.intOrNull ?: DEFAULT_SWIPE_MS)
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
        return if (action == Action.SWIPE) {
            Parsed(
                action = action,
                x = x,
                y = y,
                x2 = nonNegative(obj["x2"]?.jsonPrimitive?.intOrNull),
                y2 = nonNegative(obj["y2"]?.jsonPrimitive?.intOrNull),
                durationMs = durationMs,
            )
        } else {
            Parsed(action = action, x = x, y = y, x2 = x, y2 = y, durationMs = TAP_MS)
        }
    }

    private fun nonNegative(value: Int?): Int {
        if (value == null || value < 0) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        return value
    }

    private fun fail(message: String): ToolResult =
        ToolResult(json = """{"ok":false,"error":"$message"}""", error = message)

    private fun successJson(parsed: Parsed): String = when (parsed.action) {
        Action.TAP ->
            """{"ok":true,"action":"tap","x":${parsed.x},"y":${parsed.y}}"""
        Action.SWIPE ->
            """{"ok":true,"action":"swipe","x":${parsed.x},"y":${parsed.y},"x2":${parsed.x2},"y2":${parsed.y2}}"""
    }

    private enum class Action { TAP, SWIPE }

    private data class Parsed(
        val action: Action,
        val x: Int,
        val y: Int,
        val x2: Int,
        val y2: Int,
        val durationMs: Int,
    )

    companion object {
        const val NAME = "tap_swipe"
        const val CONSENT_REQUIRED = UserFacingErrors.TAP_SWIPE_CONSENT
        const val SERVICE_REQUIRED = UserFacingErrors.TAP_SWIPE_SERVICE
        const val BLOCKED_APP = UserFacingErrors.TAP_SWIPE_BLOCKED
        private const val TAP_MS = 50
        private const val DEFAULT_SWIPE_MS = 300
        private const val MIN_DURATION_MS = 50
        private const val MAX_DURATION_MS = 2000
        val DESCRIPTOR = ToolDescriptor(
            name = NAME,
            description = "Tap or swipe on screen like a human. Sideload only. Always needs confirmation. Forbidden on banks, payments, and password managers.",
            properties = mapOf(
                "action" to ToolParamSpec(ToolParamType.STRING),
                "x" to ToolParamSpec(ToolParamType.INTEGER),
                "y" to ToolParamSpec(ToolParamType.INTEGER),
                "x2" to ToolParamSpec(ToolParamType.INTEGER, defaultJson = "0"),
                "y2" to ToolParamSpec(ToolParamType.INTEGER, defaultJson = "0"),
                "durationMs" to ToolParamSpec(ToolParamType.INTEGER, defaultJson = "300"),
            ),
            riskLevel = RiskLevel.L3,
        )
    }
}
