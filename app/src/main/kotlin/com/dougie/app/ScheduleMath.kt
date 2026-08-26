package com.dougie.app

import java.time.Instant
import java.time.ZoneId

data class ScheduleItem(
    val id: String,
    val hour: Int,
    val minute: Int,
    val daily: Boolean,
    val draft: String,
    val oneShotEpochMillis: Long? = null,
)

fun nextDailyEpochMs(hour: Int, minute: Int, nowEpochMs: Long, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) {
        next = next.plusDays(1)
    }
    return next.toInstant().toEpochMilli()
}

fun nextTriggerEpochMs(item: ScheduleItem, nowEpochMs: Long, zone: ZoneId): Long {
    if (item.daily) {
        return nextDailyEpochMs(item.hour, item.minute, nowEpochMs, zone)
    }
    val oneShot = item.oneShotEpochMillis
    if (oneShot != null && oneShot > nowEpochMs) return oneShot
    return nextDailyEpochMs(item.hour, item.minute, nowEpochMs, zone)
}

fun afterScheduleFire(items: List<ScheduleItem>, firedId: String): List<ScheduleItem> {
    val item = items.find { it.id == firedId } ?: return items
    return if (item.daily) items else items.filter { it.id != firedId }
}

fun formatScheduleNotice(hour: Int, minute: Int): String {
    val time = "%02d:%02d".format(hour, minute)
    return "定时提醒 · $time"
}
