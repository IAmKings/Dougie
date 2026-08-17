package com.dougie.tool.system

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.dougie.core.model.AndroidPermissions
import com.dougie.core.tool.CalendarPort
import java.time.Instant
import java.time.OffsetDateTime
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class AndroidCalendarPort(
    context: Context,
    private val onUsed: (String) -> Unit = {},
) : CalendarPort {
    private val resolver = context.contentResolver

    override suspend fun queryUpcoming(limit: Int): String {
        onUsed(AndroidPermissions.READ_CALENDAR)
        val now = System.currentTimeMillis()
        val end = now + TimeUnit.DAYS.toMillis(60)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendEncodedPath(now.toString())
            .appendEncodedPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
        )
        val events = ArrayList<String>(limit.coerceAtLeast(0))
        resolver.query(
            uri,
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            while (cursor.moveToNext() && events.size < limit) {
                val id = if (idIdx >= 0) cursor.getLong(idIdx) else events.size.toLong()
                val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
                val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
                val escaped = title.replace("\\", "\\\\").replace("\"", "\\\"")
                events += """{"id":"$id","title":"$escaped","startMs":$begin}"""
            }
        }
        return """{"events":[${events.joinToString(",")}]}"""
    }

    override suspend fun createEvent(title: String, startIso: String, idempotencyKey: String): String {
        onUsed(AndroidPermissions.WRITE_CALENDAR)
        val startMs = parseStartMs(startIso) ?: return """{"ok":false,"error":"invalid_start"}"""
        val calendarId = visibleCalendarId() ?: return """{"ok":false,"error":"no_calendar"}"""
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, startMs + TimeUnit.HOURS.toMillis(1))
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val id = uri?.lastPathSegment.orEmpty()
        return """{"ok":true,"id":"$id"}"""
    }

    private fun visibleCalendarId(): Long? {
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE}=1",
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    private fun parseStartMs(startIso: String): Long? {
        return try {
            OffsetDateTime.parse(startIso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(startIso).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
