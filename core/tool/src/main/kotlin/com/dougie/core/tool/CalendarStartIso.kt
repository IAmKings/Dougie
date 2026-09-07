package com.dougie.core.tool

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Parse calendar start strings from small models. Do not log the raw value. */
object CalendarStartIso {
    fun parseToEpochMs(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.all { it.isDigit() } && text.length in 10..13) {
            return text.toLongOrNull()
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return Instant.parse(text).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        for (formatter in LOCAL_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter).atZone(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private val LOCAL_FORMATS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
    )
}
