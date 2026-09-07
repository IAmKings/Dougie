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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
        if (json.contains("\"ok\":false")) {
            val message = when {
                json.contains("invalid_start") -> UserFacingErrors.CALENDAR_INVALID_START
                json.contains("no_calendar") -> UserFacingErrors.CALENDAR_NONE
                else -> UserFacingErrors.TOOL_FAILED
            }
            return ToolResult(json = json, isFatal = true, error = message)
        }
        idempotencyStore.put(context.idempotencyKey, json)
        val stored = idempotencyStore.get(context.idempotencyKey) ?: json
        return ToolResult(json = stored)
    }

    private fun parseArgs(argumentsJson: String): Parsed {
        val obj = try {
            Json.parseToJsonElement(canonicalizeArgs(argumentsJson)).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val startIso = obj["startIso"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isEmpty() || startIso.isEmpty()) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        CalendarStartIso.parseToEpochMs(startIso)
            ?: throw AgentException(UserFacingErrors.CALENDAR_INVALID_START)
        return Parsed(title = title, startIso = startIso)
    }

    private data class Parsed(val title: String, val startIso: String)

    companion object {
        const val NAME = "calendar_create"

        fun canonicalizeArgs(rawArgsJson: String): String {
            val obj = try {
                Json.parseToJsonElement(rawArgsJson.trim().ifEmpty { "{}" }).jsonObject
            } catch (_: Exception) {
                return rawArgsJson
            }
            val title = firstString(obj, "title", "name", "event")
            val startIso = firstString(obj, "startIso", "start_iso", "startISO", "datetime", "start")
            if (title.isNullOrEmpty() && startIso.isNullOrEmpty()) return rawArgsJson
            return buildJsonObject {
                obj.forEach { (key, value) -> put(key, value) }
                if (!title.isNullOrEmpty()) put("title", title)
                if (!startIso.isNullOrEmpty()) put("startIso", startIso)
            }.toString()
        }

        private fun firstString(
            obj: JsonObject,
            vararg keys: String,
        ): String? {
            for (key in keys) {
                val value = obj[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (value.isNotEmpty()) return value
            }
            return null
        }
    }
}
