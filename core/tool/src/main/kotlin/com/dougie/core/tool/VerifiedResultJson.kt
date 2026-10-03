package com.dougie.core.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Verified-flag helpers shared by tools that run post-action verification. The flag is
 * mirrored into the result JSON (only when set, so channels without verification keep
 * byte-identical payloads) and survives idempotent replays through the store.
 */
object VerifiedResultJson {
    /** Inject `"verified":<verified>` into a tool result JSON object. */
    fun withFlag(json: String, verified: Boolean): String {
        val obj = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return json
        }
        return JsonObject(obj + ("verified" to JsonPrimitive(verified))).toString()
    }

    /** Read the `"verified"` flag back; missing key or unparseable JSON → null. */
    fun fromJson(json: String): Boolean? {
        return try {
            Json.parseToJsonElement(json).jsonObject["verified"]?.jsonPrimitive?.booleanOrNull
        } catch (_: Exception) {
            null
        }
    }
}
