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
                    val fallback = spec.defaultJson ?: continue
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
        if (type == ToolParamType.OBJECT) return value
        val prim = value as? JsonPrimitive ?: return null
        return when (type) {
            ToolParamType.STRING -> JsonPrimitive(prim.content)
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

    companion object {
        private const val EMPTY_OBJECT = "{}"
    }
}
