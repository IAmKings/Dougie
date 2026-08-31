package com.dougie.core.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object IntentRouteAnswers {
    private const val TRAILING_PUNCT = "？?！!。．.…、，, "
    private const val TRAILING_PARTICLE = "了呢啊呀吗吧嘛的"
    private const val CLIPBOARD_MAX = 200
    private val eventTimeFmt = DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault())

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
        "query_calendar" -> "calendar_query"
        "clipboard_read" -> "clipboard_read"
        "query_location" -> "location"
        else -> null
    }

    fun formatFinalAnswer(toolName: String, resultJson: String): String? {
        val obj = try {
            Json.parseToJsonElement(resultJson).jsonObject
        } catch (_: Exception) {
            return null
        }
        return try {
            when (toolName) {
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
                "calendar_query" -> formatCalendar(obj)
                "clipboard_read" -> formatClipboard(obj)
                "location" -> formatLocation(obj)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatCalendar(obj: JsonObject): String? {
        val events = obj["events"]?.jsonArray ?: return null
        if (events.isEmpty()) return "最近没有日程。"
        val lines = events.take(5).mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifEmpty { "未命名" }
            val startMs = item["startMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            "· $title（${eventTimeFmt.format(Instant.ofEpochMilli(startMs))}）"
        }
        if (lines.isEmpty()) return null
        val extra = events.size - lines.size
        val more = if (extra > 0) "\n另外还有 ${extra} 条。" else ""
        return "最近日程：\n${lines.joinToString("\n")}$more"
    }

    private fun formatClipboard(obj: JsonObject): String? {
        if (obj["ok"]?.jsonPrimitive?.booleanOrNull != true) return null
        val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return null
        if (text.isEmpty()) return "剪贴板是空的。"
        val shown = if (text.length <= CLIPBOARD_MAX) text else text.take(CLIPBOARD_MAX) + "…"
        return "剪贴板内容：$shown"
    }

    private fun formatLocation(obj: JsonObject): String? {
        if (obj["ok"]?.jsonPrimitive?.booleanOrNull != true) return null
        val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val acc = obj["accuracy_m"]?.jsonPrimitive?.doubleOrNull ?: return null
        val accLabel = if (acc % 1.0 == 0.0) acc.toInt().toString() else acc.toString()
        return "大约在纬度 $lat、经度 $lon（精度约 ${accLabel} 米）。"
    }
}
