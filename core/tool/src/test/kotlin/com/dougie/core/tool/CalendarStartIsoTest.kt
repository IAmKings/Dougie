package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CalendarStartIsoTest {
    @Test
    fun parsesOffsetAndLocalAndEpoch() {
        val zone = ZoneOffset.ofHours(8)
        assertNotNull(CalendarStartIso.parseToEpochMs("2026-09-08T15:00:00+08:00", zone))
        assertNotNull(CalendarStartIso.parseToEpochMs("2026-09-08T15:00:00", zone))
        assertNotNull(CalendarStartIso.parseToEpochMs("2026-09-08 15:00", zone))
        assertNotNull(CalendarStartIso.parseToEpochMs("1694163600000", zone))
        assertNull(CalendarStartIso.parseToEpochMs("明天下午三点", zone))
    }

    @Test
    fun canonicalizeMapsSnakeCaseStart() {
        val json = CalendarCreateTool.canonicalizeArgs(
            """{"title":"开会","start_iso":"2026-09-08T15:00:00"}""",
        )
        assertTrue(json.contains("\"startIso\""))
        assertTrue(json.contains("开会"))
    }
}
