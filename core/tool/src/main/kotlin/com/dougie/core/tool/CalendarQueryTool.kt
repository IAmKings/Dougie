package com.dougie.core.tool

import com.dougie.core.model.AndroidPermissions
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamSpec
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CalendarQueryTool(
    private val port: CalendarPort,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = NAME,
        description = "Query upcoming calendar events as a short JSON summary.",
        properties = mapOf(
            "limit" to ToolParamSpec(ToolParamType.INTEGER, defaultJson = "10"),
        ),
        riskLevel = RiskLevel.L1,
        androidPermission = AndroidPermissions.READ_CALENDAR,
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val limit = parseLimit(argumentsJson)
        val json = port.queryUpcoming(limit)
        return ToolResult(json = json)
    }

    private fun parseLimit(argumentsJson: String): Int {
        val trimmed = argumentsJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return 10
        return try {
            val value = Json.parseToJsonElement(trimmed).jsonObject["limit"]?.jsonPrimitive?.intOrNull
            value?.coerceIn(1, 50) ?: 10
        } catch (_: Exception) {
            10
        }
    }

    companion object {
        const val NAME = "calendar_query"
    }
}
