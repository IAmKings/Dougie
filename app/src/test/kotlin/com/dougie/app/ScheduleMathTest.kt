package com.dougie.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ScheduleMathTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun dailyUsesTodayIfStillAheadOtherwiseTomorrow() {
        val midnight = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
        val nine = Instant.parse("2024-01-01T09:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2024-01-01T08:00:00Z").toEpochMilli(),
            nextDailyEpochMs(8, 0, midnight, utc),
        )
        assertEquals(
            Instant.parse("2024-01-02T08:00:00Z").toEpochMilli(),
            nextDailyEpochMs(8, 0, nine, utc),
        )
        assertTrue(
            nextTriggerEpochMs(
                ScheduleItem("x", 8, 0, daily = true, draft = ""),
                nine,
                utc,
            ) > nine,
        )
    }

    @Test
    fun afterFireRemovesOneShotKeepsDaily() {
        val daily = ScheduleItem("d", 7, 30, daily = true, draft = "秘密草稿")
        val once = ScheduleItem("o", 7, 30, daily = false, draft = "秘密草稿", oneShotEpochMillis = 1L)
        assertEquals(listOf(daily), afterScheduleFire(listOf(daily, once), "o"))
        assertEquals(listOf(daily, once), afterScheduleFire(listOf(daily, once), "d"))
    }

    @Test
    fun noticeOmitsDraft() {
        val line = formatScheduleNotice(7, 5)
        assertEquals("定时提醒 · 07:05", line)
        assertFalse(line.contains("现在几点了"))
        val encoded = ScheduleCodec.encode(
            listOf(ScheduleItem("id", 7, 5, false, "现在几点了", 99L)),
        )
        assertTrue(encoded.contains(java.util.Base64.getEncoder().encodeToString("现在几点了".toByteArray(Charsets.UTF_8))))
        assertFalse(encoded.contains("现在几点了"))
        val decoded = ScheduleCodec.decode(encoded).single()
        assertEquals("现在几点了", decoded.draft)
        assertEquals(99L, decoded.oneShotEpochMillis)
        assertFalse(formatScheduleNotice(decoded.hour, decoded.minute).contains(decoded.draft))
    }

    @Test
    fun pendingDraftSurvivesOneShotRemoval() {
        val dir = kotlin.io.path.createTempDirectory("dougie-sched").toFile()
        try {
            val store = ScheduleStore(dir)
            val item = ScheduleItem("o", 7, 0, daily = false, draft = "填入草稿", oneShotEpochMillis = 1L)
            assertTrue(store.add(item))
            store.putPendingDraft(item.id, item.draft)
            store.save(afterScheduleFire(store.list(), item.id))
            assertEquals(null, store.find("o"))
            assertEquals("填入草稿", store.draftForNotificationTap("o"))
            assertEquals(null, store.draftForNotificationTap("o"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
