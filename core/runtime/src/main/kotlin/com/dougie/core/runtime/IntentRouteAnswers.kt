package com.dougie.core.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object IntentRouteAnswers {
    private const val TRAILING_PUNCT = "？?！!。．.…、，, "
    private const val TRAILING_PARTICLE = "了呢啊呀吗吧嘛的"

    fun classifyTexts(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()
        val normalized = normalize(trimmed)
        return if (normalized.isEmpty() || normalized == trimmed) {
            listOf(trimmed)
        } else {
            listOf(trimmed, normalized)
        }
    }

    fun normalize(text: String): String {
        var s = text.trim()
        while (s.isNotEmpty()) {
            val last = s.last()
            if (last !in TRAILING_PUNCT && last !in TRAILING_PARTICLE) break
            s = s.dropLast(1).trimEnd()
        }
        return s
    }

    fun toolNameFor(intent: String): String? = when (intent) {
        "query_time" -> "time"
        "query_battery" -> "battery"
        else -> null
    }

    fun formatFinalAnswer(toolName: String, resultJson: String): String? {
        val obj = try {
            Json.parseToJsonElement(resultJson).jsonObject
        } catch (_: Exception) {
            return null
        }
        return when (toolName) {
            "time" -> {
                val iso = obj["iso_local"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val zone = obj["zone"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (iso.isEmpty() || zone.isEmpty()) null
                else "现在是 $iso，时区 $zone。"
            }
            "battery" -> {
                val percent = obj["battery_percent"]?.jsonPrimitive?.intOrNull ?: return null
                val charging = obj["charging"]?.jsonPrimitive?.booleanOrNull ?: return null
                val chargeText = if (charging) "正在充电。" else "未在充电。"
                "当前电量 $percent%。$chargeText"
            }
            else -> null
        }
    }
}
