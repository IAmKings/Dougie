package com.dougie.core.runtime

import com.dougie.core.tool.OpenAppEntry
import com.dougie.core.tool.OpenAppEntries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
        "create_calendar" -> "calendar_create"
        "clipboard_write" -> "clipboard_write"
        "open_app" -> "app_intent"
        "screen_capture" -> "screen_capture"
        else -> null
    }

    fun parseShortcutArgs(
        toolName: String,
        input: String,
        openApps: List<OpenAppEntry> = emptyList(),
    ): String? = when (toolName) {
        "time", "battery", "calendar_query", "clipboard_read", "location", "screen_capture" -> "{}"
        "clipboard_write" -> parseClipboardWrite(input)
        "calendar_create" -> parseCalendarCreate(input)
        "app_intent" -> parseOpenApp(input, openApps)
        else -> null
    }

    fun formatFinalAnswer(
        toolName: String,
        resultJson: String,
        openApps: List<OpenAppEntry> = emptyList(),
    ): String? {
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
                "calendar_create" -> formatCalendarCreate(obj)
                "clipboard_write" -> formatClipboardWrite(obj)
                "app_intent" -> formatAppIntent(obj, openApps)
                "screen_capture" -> formatScreenCapture(obj)
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

    private fun formatCalendarCreate(obj: JsonObject): String? {
        if (obj["ok"]?.jsonPrimitive?.booleanOrNull != true) return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isEmpty()) return null
        return "已创建日程：$title。"
    }

    private fun formatClipboardWrite(obj: JsonObject): String? {
        if (obj["ok"]?.jsonPrimitive?.booleanOrNull != true) return null
        return "已写入剪贴板。"
    }

    private fun formatScreenCapture(obj: JsonObject): String? {
        val id = obj["capture_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (id.isEmpty()) return null
        obj["width"]?.jsonPrimitive?.intOrNull ?: return null
        obj["height"]?.jsonPrimitive?.intOrNull ?: return null
        return "已截取屏幕。"
    }

    private fun formatAppIntent(obj: JsonObject, openApps: List<OpenAppEntry>): String? {
        if (obj["ok"]?.jsonPrimitive?.booleanOrNull != true) return null
        val launched = obj["launched"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val pkg = launched.removePrefix("package:")
        val alias = openApps.firstOrNull { it.packageName == pkg }?.alias
        return if (alias.isNullOrEmpty()) "已打开该应用。" else "已打开$alias。"
    }

    private fun parseOpenApp(input: String, openApps: List<OpenAppEntry>): String? {
        if (openApps.isEmpty()) return null
        val hit = OpenAppEntries.match(normalize(input), openApps) ?: return null
        return buildJsonObject {
            put("uri", "package:${hit.packageName}")
        }.toString()
    }

    private fun parseClipboardWrite(input: String): String? {
        val text = firstQuoted(input) ?: return null
        return buildJsonObject { put("text", text) }.toString()
    }

    private fun firstQuoted(s: String): String? {
        var i = 0
        while (i < s.length) {
            val close = when (s[i]) {
                '「' -> '」'
                '『' -> '』'
                '"' -> '"'
                '\'' -> '\''
                else -> null
            }
            if (close != null) {
                val j = s.indexOf(close, startIndex = i + 1)
                if (j > i + 1) {
                    val inner = s.substring(i + 1, j).trim()
                    if (inner.isNotEmpty()) return inner
                }
            }
            i++
        }
        return null
    }

    private fun parseCalendarCreate(input: String): String? {
        val clock = findClock(input) ?: return null
        val hour = applyPeriod(clock.hour, periodNear(input, clock.start)) ?: return null
        if (hour !in 0..23 || clock.minute !in 0..59) return null
        val days = when {
            input.contains("后天") -> 2L
            input.contains("明天") -> 1L
            else -> 0L
        }
        val zone = ZoneId.systemDefault()
        val startIso = ZonedDateTime.of(
            LocalDate.now(zone).plusDays(days),
            LocalTime.of(hour, clock.minute),
            zone,
        ).toOffsetDateTime().toString()
        val title = calendarTitle(input, clock) ?: return null
        return buildJsonObject {
            put("title", title)
            put("startIso", startIso)
        }.toString()
    }

    private data class ClockHit(val start: Int, val end: Int, val hour: Int, val minute: Int)

    private fun findClock(s: String): ClockHit? {
        val hits = ArrayList<ClockHit>()
        COLON_CLOCK.findAll(s).forEach { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = m.groupValues[2].toIntOrNull() ?: return@forEach
            hits += ClockHit(m.range.first, m.range.last + 1, hour, minute)
        }
        POINT_CLOCK.findAll(s).forEach { m ->
            val hour = parseCnInt(m.groupValues[1]) ?: return@forEach
            val minute = when {
                m.groupValues[2] == "半" -> 30
                m.groupValues[3].isNotEmpty() -> parseCnInt(m.groupValues[3]) ?: return@forEach
                else -> 0
            }
            hits += ClockHit(m.range.first, m.range.last + 1, hour, minute)
        }
        return hits.minByOrNull { it.start }
    }

    private fun periodNear(s: String, clockStart: Int): String? {
        val prefix = s.substring(0, clockStart.coerceIn(0, s.length))
        var best: Pair<Int, String>? = null
        for (word in PERIODS) {
            val at = prefix.lastIndexOf(word)
            if (at >= 0 && (best == null || at > best.first)) best = at to word
        }
        return best?.second
    }

    private fun applyPeriod(hour: Int, period: String?): Int? {
        if (hour !in 0..23) return null
        return when (period) {
            "下午", "傍晚" -> if (hour in 1..11) hour + 12 else hour
            "晚上", "今晚" -> when (hour) {
                12 -> 0
                in 1..11 -> hour + 12
                else -> hour
            }
            "上午", "早上", "今早" -> if (hour == 12) 0 else hour
            else -> hour
        }
    }

    private fun calendarTitle(input: String, clock: ClockHit): String? {
        val stripped = buildString {
            append(input.substring(0, clock.start))
            append(input.substring(clock.end))
        }
        var rest = stripped
        for (noise in TITLE_NOISE) {
            rest = rest.replace(noise, "")
        }
        rest = rest.replace(Regex("[\\s？?！!。．.…、，,]+"), "").trim()
        if (rest.isNotEmpty()) return rest
        if (input.contains("开会") || input.contains("会议")) return "开会"
        return null
    }

    private fun parseCnInt(raw: String): Int? {
        if (raw.all { it.isDigit() }) return raw.toIntOrNull()
        return when (raw.length) {
            1 -> CN_DIGIT[raw[0]]
            2 -> when {
                raw[0] == '十' -> {
                    val ones = CN_DIGIT[raw[1]] ?: return null
                    if (ones == 10) null else 10 + ones
                }
                raw[1] == '十' -> {
                    val tens = CN_DIGIT[raw[0]] ?: return null
                    if (tens !in 1..5) null else tens * 10
                }
                else -> null
            }
            3 -> {
                if (raw[1] != '十') return null
                val tens = CN_DIGIT[raw[0]] ?: return null
                val ones = CN_DIGIT[raw[2]] ?: return null
                if (tens !in 1..5 || ones == 10) null else tens * 10 + ones
            }
            else -> null
        }
    }

    private val COLON_CLOCK = Regex("""(\d{1,2})[:：](\d{2})""")
    private val POINT_CLOCK = Regex(
        """(\d{1,2}|[零一二三四五六七八九十两]{1,3})点(半|(?:(\d{1,2}|[零一二三四五六七八九十]{1,3})分))?""",
    )
    private val PERIODS = listOf("今早", "今晚", "傍晚", "晚上", "下午", "上午", "早上", "中午")
    private val TITLE_NOISE = listOf(
        "帮我", "请", "给我", "麻烦",
        "定个日程", "创建日程", "加个日程", "安排个日程", "安排日程",
        "提醒我", "后天", "明天", "今天", "今晚", "今早",
        "傍晚", "晚上", "下午", "上午", "早上", "中午",
        "点钟", "安排", "日程",
    )
    private val CN_DIGIT = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
    )
}
