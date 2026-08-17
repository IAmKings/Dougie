package com.dougie.data.tasks

import android.content.ContentValues
import com.dougie.core.runtime.AuditEntry
import com.dougie.core.runtime.AuditLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SqliteAuditLog(
    private val helper: TaskDbHelper,
) : AuditLog {
    override fun record(taskId: String, toolName: String, outcome: String) {
        val values = ContentValues().apply {
            put("task_id", taskId)
            put("tool_name", toolName)
            put("outcome", outcome)
            put("created_at", System.currentTimeMillis())
        }
        helper.writableDatabase.insert("audit_log", null, values)
    }

    override suspend fun listRecent(limit: Int): List<AuditEntry> = withContext(Dispatchers.IO) {
        val bounded = limit.coerceIn(0, 500)
        helper.readableDatabase.rawQuery(
            """
            SELECT task_id, tool_name, outcome, created_at
            FROM audit_log
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(bounded.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        AuditEntry(
                            taskId = cursor.getString(0).orEmpty(),
                            toolName = cursor.getString(1).orEmpty(),
                            outcome = cursor.getString(2).orEmpty(),
                            createdAt = cursor.getLong(3),
                        ),
                    )
                }
            }
        }
    }
}
