package com.dougie.data.tasks

import android.content.ContentValues
import com.dougie.core.runtime.AuditLog

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
}
