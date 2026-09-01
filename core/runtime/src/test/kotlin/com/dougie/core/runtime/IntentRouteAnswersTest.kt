package com.dougie.core.runtime

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class IntentRouteAnswersTest {
    @Test
    fun timeExampleNormalizesToProbePhrase() {
        assertEquals("现在几点", IntentRouteAnswers.normalize("现在几点了？"))
        assertEquals(
            listOf("现在几点了？", "现在几点"),
            IntentRouteAnswers.classifyTexts("现在几点了？"),
        )
    }

    @Test
    fun mapsL1QueryIntentsToTools() {
        assertEquals("calendar_query", IntentRouteAnswers.toolNameFor("query_calendar"))
        assertEquals("clipboard_read", IntentRouteAnswers.toolNameFor("clipboard_read"))
        assertEquals("location", IntentRouteAnswers.toolNameFor("query_location"))
        assertEquals("calendar_create", IntentRouteAnswers.toolNameFor("create_calendar"))
        assertEquals("clipboard_write", IntentRouteAnswers.toolNameFor("clipboard_write"))
        assertEquals(null, IntentRouteAnswers.toolNameFor("open_app"))
        assertEquals(null, IntentRouteAnswers.toolNameFor("screen_capture"))
        assertEquals(null, IntentRouteAnswers.toolNameFor("speech_input"))
    }

    @Test
    fun formatsEmptyCalendarAndClipboard() {
        assertEquals(
            "最近没有日程。",
            IntentRouteAnswers.formatFinalAnswer("calendar_query", """{"events":[]}"""),
        )
        assertEquals(
            null,
            IntentRouteAnswers.formatFinalAnswer("calendar_query", """{"events":[1]}"""),
        )
        assertEquals(
            "剪贴板是空的。",
            IntentRouteAnswers.formatFinalAnswer("clipboard_read", """{"ok":true,"text":""}"""),
        )
        val long = "a".repeat(201)
        val clip = IntentRouteAnswers.formatFinalAnswer(
            "clipboard_read",
            """{"ok":true,"text":"$long"}""",
        )
        assertEquals(true, clip!!.endsWith("…"))
        assertEquals(true, clip.startsWith("剪贴板内容："))
        assertEquals(true, clip.length <= "剪贴板内容：".length + 200 + 1)
    }

    @Test
    fun formatsLocation() {
        assertEquals(
            "大约在纬度 31.23、经度 121.47（精度约 500 米）。",
            IntentRouteAnswers.formatFinalAnswer(
                "location",
                """{"ok":true,"latitude":31.23,"longitude":121.47,"accuracy_m":500.0,"provider":"network"}""",
            ),
        )
    }

    @Test
    fun queryToolsStillUseEmptyArgs() {
        assertEquals("{}", IntentRouteAnswers.parseShortcutArgs("time", "现在几点"))
        assertEquals("{}", IntentRouteAnswers.parseShortcutArgs("calendar_query", "今天有什么日程"))
    }

    @Test
    fun clipboardWriteNeedsQuotedText() {
        val json = IntentRouteAnswers.parseShortcutArgs("clipboard_write", "把『你好』写到剪贴板")
        val text = Json.parseToJsonElement(json!!).jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("你好", text)
        assertNull(IntentRouteAnswers.parseShortcutArgs("clipboard_write", "帮我复制"))
        assertNull(IntentRouteAnswers.parseShortcutArgs("clipboard_write", "把「  」写到剪贴板"))
        assertEquals(
            "已写入剪贴板。",
            IntentRouteAnswers.formatFinalAnswer("clipboard_write", """{"ok":true}"""),
        )
    }

    @Test
    fun calendarCreateNeedsClock() {
        assertNull(IntentRouteAnswers.parseShortcutArgs("calendar_create", "帮我定个日程"))
        val json = IntentRouteAnswers.parseShortcutArgs("calendar_create", "明天下午三点开会")!!
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("开会", obj["title"]!!.jsonPrimitive.content)
        val zone = ZoneId.systemDefault()
        val expected = ZonedDateTime.of(
            LocalDate.now(zone).plusDays(1),
            LocalTime.of(15, 0),
            zone,
        ).toOffsetDateTime().toString()
        assertEquals(expected, obj["startIso"]!!.jsonPrimitive.content)
        assertEquals(
            "已创建日程：开会。",
            IntentRouteAnswers.formatFinalAnswer(
                "calendar_create",
                """{"ok":true,"id":"1","title":"开会"}""",
            ),
        )
    }
}
