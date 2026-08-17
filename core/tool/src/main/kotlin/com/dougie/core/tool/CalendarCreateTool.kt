package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.AndroidPermissions
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

class CalendarCreateTool(
    private val port: CalendarPort,
    private val idempotencyStore: IdempotencyStore = InMemoryIdempotencyStore(),
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = NAME,
        description = "Create a calendar event. Requires title and startIso.",
        properties = mapOf(
            "title" to ToolParamSpec(ToolParamType.STRING),
            "startIso" to ToolParamSpec(ToolParamType.STRING),
        ),
        riskLevel = RiskLevel.L2,
        androidPermission = AndroidPermissions.WRITE_CALENDAR,
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        idempotencyStore.get(context.idempotencyKey)?.let { return ToolResult(json = it) }
        val parsed = parseArgs(argumentsJson)
        val json = port.createEvent(
            title = parsed.title,
            startIso = parsed.startIso,
            idempotencyKey = context.idempotencyKey,
        )
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
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val startIso = obj["startIso"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isEmpty() || startIso.isEmpty()) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        return Parsed(title = title, startIso = startIso)
    }

    private data class Parsed(val title: String, val startIso: String)

    companion object {
        const val NAME = "calendar_create"
    }
}
