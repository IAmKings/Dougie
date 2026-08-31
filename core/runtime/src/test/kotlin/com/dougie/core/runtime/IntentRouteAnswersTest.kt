package com.dougie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals(null, IntentRouteAnswers.toolNameFor("create_calendar"))
        assertEquals(null, IntentRouteAnswers.toolNameFor("clipboard_write"))
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
}
