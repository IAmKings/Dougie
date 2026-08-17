package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object IntentPrompt {
    fun render(userText: String): String =
        "Output JSON only with keys intent, slots, route, confidence. Thinking off.\nUser: $userText"
}

object IntentJsonParser {
    fun parse(raw: String): IntentHit {
        var searchFrom = 0
        while (searchFrom < raw.length) {
            val blob = extractObject(raw, searchFrom) ?: throw AgentException(UserFacingErrors.INTENT_FAILED)
            val obj = try {
                Json.parseToJsonElement(blob).jsonObject
            } catch (_: Exception) {
                searchFrom = raw.indexOf('{', searchFrom) + 1
                continue
            }
            return decode(obj)
        }
        throw AgentException(UserFacingErrors.INTENT_FAILED)
    }

    private fun decode(obj: JsonObject): IntentHit {
        return try {
            val intent = obj["intent"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val route = obj["route"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            if (intent.isEmpty() || route.isEmpty()) {
                throw AgentException(UserFacingErrors.INTENT_FAILED)
            }
            val slots = obj["slots"]?.jsonObject?.mapNotNull { (key, value) ->
                val text = value.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                key to text
            }?.toMap().orEmpty()
            IntentHit(intent = intent, slots = slots, route = route, confidence = confidence)
        } catch (e: AgentException) {
            throw e
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
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
