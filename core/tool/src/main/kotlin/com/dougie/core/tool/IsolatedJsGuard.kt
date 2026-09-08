package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object IsolatedJsGuard {
    const val SCRIPT_MAX = 8 * 1024
    const val DATA_MAX = 32 * 1024
    private val HOST = listOf(
        "fetch(",
        "xmlhttprequest",
        "websocket",
        "java.type",
        "java.",
        "packages.",
        "android.",
        "javax.",
        "kotlin.",
    )
    private val FUNCTION_BODY = Regex("""^\s*return\b""")

    fun assertSize(script: String, dataJson: String) {
        if (script.length > SCRIPT_MAX || dataJson.length > DATA_MAX) {
            throw AgentException(UserFacingErrors.JS_EVAL_TOO_LARGE)
        }
    }

    fun assertNoHost(script: String) {
        val lower = script.lowercase()
        if (HOST.any { lower.contains(it) }) {
            throw AgentException(UserFacingErrors.JS_EVAL_HOST)
        }
    }

    /** JSON document, or comma-separated JSON values like `1,2` → `[1,2]`. */
    fun canonicalizeData(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        completeJsonDocument(trimmed)?.let { return it }
        val parts = splitTopLevelComma(trimmed)
        if (parts.size < 2) throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        val els = parts.map { part ->
            val piece = part.trim()
            completeJsonDocument(piece)
                ?: throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
            Json.parseToJsonElement(piece)
        }
        return JsonArray(els).toString()
    }

    fun wrapProgram(script: String, dataJson: String): String =
        "JSON.stringify((function(data){\n$script\n})(JSON.parse(${quoteJs(dataJson)})))"

    /**
     * Direct `eval` so `var data` in this function is in scope.
     * `(0,eval)` is global and cannot see `data` → `data.reduce` throws.
     */
    fun wrapAsProgram(script: String, dataJson: String): String =
        "(function(){var data=JSON.parse(${quoteJs(dataJson)});return JSON.stringify(eval(${quoteJs(script)}));})()"

    fun wrapForExecute(script: String, dataJson: String, asProgram: Boolean): String {
        if (!asProgram || FUNCTION_BODY.containsMatchIn(script)) {
            return wrapProgram(script, dataJson)
        }
        return wrapAsProgram(script, dataJson)
    }

    fun quoteJs(raw: String): String {
        val out = StringBuilder(raw.length + 2)
        out.append('"')
        for (c in raw) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c.code < 0x20) {
                    out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(c)
                }
            }
        }
        out.append('"')
        return out.toString()
    }

    private fun completeJsonDocument(trimmed: String): String? {
        val el = try {
            Json.parseToJsonElement(trimmed)
        } catch (_: Exception) {
            return null
        }
        if (hasUnparsedRest(trimmed, el)) return null
        return el.toString()
    }

    private fun hasUnparsedRest(raw: String, el: JsonElement): Boolean {
        if (el is JsonPrimitive && !el.isString) {
            val encoded = el.toString()
            if (raw.startsWith(encoded)) {
                return raw.substring(encoded.length).trim().isNotEmpty()
            }
        }
        return false
    }

    private fun splitTopLevelComma(raw: String): List<String> {
        val parts = ArrayList<String>()
        val buf = StringBuilder()
        var depth = 0
        var inStr = false
        var escape = false
        for (c in raw) {
            if (inStr) {
                buf.append(c)
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inStr = false
                }
                continue
            }
            when (c) {
                '"' -> {
                    inStr = true
                    buf.append(c)
                }
                '{', '[' -> {
                    depth++
                    buf.append(c)
                }
                '}', ']' -> {
                    depth--
                    buf.append(c)
                }
                ',' -> if (depth == 0) {
                    parts.add(buf.toString())
                    buf.clear()
                } else {
                    buf.append(c)
                }
                else -> buf.append(c)
            }
        }
        parts.add(buf.toString())
        return parts
    }
}
