package com.dougie.core.runtime

import com.dougie.core.model.AgentException
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

class ToolCallSanitizer(
    private val descriptors: Map<String, ToolDescriptor>,
) {
    fun sanitize(name: String, rawArgsJson: String): String {
        descriptors[name] ?: throw AgentException(UserFacingErrors.UNKNOWN_TOOL)
        val descriptor = descriptors.getValue(name)
        val parsed = parseObjectOrEmpty(rawArgsJson)
        if (descriptor.properties.isEmpty()) {
            return EMPTY_OBJECT
        }
        return buildJsonObject {
            for ((key, spec) in descriptor.properties) {
                val raw = parsed[key]
                if (raw == null || raw is JsonNull) {
                    val fallback = spec.defaultJson
                        ?: throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
                    put(key, Json.parseToJsonElement(fallback))
                    continue
                }
                val coerced = coerce(raw, spec.type)
                    ?: spec.defaultJson?.let { Json.parseToJsonElement(it) }
                    ?: throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
                put(key, coerced)
            }
        }.toString()
    }

    private fun parseObjectOrEmpty(rawArgsJson: String): JsonObject {
        val trimmed = rawArgsJson.trim()
        if (trimmed.isEmpty()) return JsonObject(emptyMap())
        return try {
            val element = Json.parseToJsonElement(trimmed)
            element as? JsonObject ?: JsonObject(emptyMap())
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }
    }

    private fun coerce(value: JsonElement, type: ToolParamType): JsonElement? {
        if (type == ToolParamType.OBJECT) {
            if (value.toString().length > MAX_OBJECT_CHARS) {
                throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
            }
            if (jsonDepth(value) > MAX_OBJECT_DEPTH) {
                throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
            }
            return value
        }
        val prim = value as? JsonPrimitive ?: return null
        return when (type) {
            ToolParamType.STRING -> {
                // Reject rather than truncate: silently shortening a user payload
                // (SMS body, clipboard text) would corrupt the action.
                if (prim.content.length > MAX_STRING_CHARS) {
                    throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
                }
                JsonPrimitive(prim.content)
            }
            ToolParamType.INTEGER -> {
                prim.intOrNull?.let { JsonPrimitive(it) }
                    ?: prim.contentOrNull?.toIntOrNull()?.let { JsonPrimitive(it) }
            }
            ToolParamType.NUMBER -> {
                prim.doubleOrNull?.let { JsonPrimitive(it) }
                    ?: prim.contentOrNull?.toDoubleOrNull()?.let { JsonPrimitive(it) }
            }
            ToolParamType.BOOLEAN -> {
                prim.booleanOrNull?.let { JsonPrimitive(it) }
                    ?: when (prim.contentOrNull?.lowercase()) {
                        "true" -> JsonPrimitive(true)
                        "false" -> JsonPrimitive(false)
                        else -> null
                    }
            }
            ToolParamType.OBJECT -> value
        }
    }

    private fun jsonDepth(value: JsonElement): Int = when (value) {
        is JsonObject -> 1 + (value.values.maxOfOrNull { jsonDepth(it) } ?: 0)
        is kotlinx.serialization.json.JsonArray -> 1 + (value.maxOfOrNull { jsonDepth(it) } ?: 0)
        else -> 0
    }

    companion object {
        private const val EMPTY_OBJECT = "{}"

        // js_eval/py_eval enforce their own 32KiB data budget; this sits above it and
        // only stops pathological multi-megabyte payloads from reaching any layer.
        private const val MAX_STRING_CHARS = 10_000
        private const val MAX_OBJECT_CHARS = 64_000
        private const val MAX_OBJECT_DEPTH = 8
    }
}
