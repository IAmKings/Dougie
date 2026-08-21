package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object IntentJsonParser {
    fun parse(raw: String): IntentHit {
        val text = stripThink(raw).replace("```json", "").replace("```", "")
        var searchFrom = 0
        while (searchFrom < text.length) {
            val blob = extractObject(text, searchFrom) ?: throw AgentException(UserFacingErrors.INTENT_FAILED)
            val obj = try {
                Json.parseToJsonElement(blob).jsonObject
            } catch (_: Exception) {
                searchFrom = text.indexOf('{', searchFrom) + 1
                continue
            }
            return decode(obj)
        }
        throw AgentException(UserFacingErrors.INTENT_FAILED)
    }

    private fun decode(obj: JsonObject): IntentHit {
        return try {
            val intent = obj["intent"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (intent.isEmpty()) {
                throw AgentException(UserFacingErrors.INTENT_FAILED)
            }
            val route = obj["route"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifEmpty { intent }
            val confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull
                ?: obj["confidence"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                ?: 0.0
            val slotsEl = obj["slots"]
            val slots = if (slotsEl is JsonObject) {
                slotsEl.mapNotNull { (key, value) ->
                    val text = value.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                    key to text
                }.toMap()
            } else {
                emptyMap()
            }
            IntentHit(intent = intent, slots = slots, route = route, confidence = confidence)
        } catch (e: AgentException) {
            throw e
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
    }

    private fun stripThink(raw: String): String {
        var text = raw
        while (true) {
            val start = text.indexOf("<think>")
            if (start < 0) break
            val end = text.indexOf("</think>", start)
            text = if (end < 0) {
                text.removeRange(start, text.length)
            } else {
                text.removeRange(start, end + "</think>".length)
            }
        }
        return text
    }

    private fun extractObject(raw: String, from: Int): String? {
        val start = raw.indexOf('{', from)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until raw.length) {
            val ch = raw[i]
            if (inString) {
                when {
                    escape -> escape = false
                    ch == '\\' -> escape = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
