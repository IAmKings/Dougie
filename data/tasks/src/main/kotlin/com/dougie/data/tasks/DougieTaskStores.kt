package com.dougie.data.tasks

import android.content.Context
import com.dougie.core.runtime.AuditLog
import com.dougie.core.runtime.TaskStore
import com.dougie.core.tool.IdempotencyStore

class DougieTaskStores(context: Context) {
    private val helper = TaskDbHelper(context)
    val taskStore: TaskStore = SqliteTaskStore(helper)
    val idempotencyStore: IdempotencyStore = SqliteIdempotencyStore(helper)
    val auditLog: AuditLog = SqliteAuditLog(helper)
}
