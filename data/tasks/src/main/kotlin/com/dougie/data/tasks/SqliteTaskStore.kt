package com.dougie.data.tasks

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.dougie.core.model.AgentTask
import com.dougie.core.runtime.TaskSnapshotCodec
import com.dougie.core.runtime.TaskStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SqliteTaskStore(
    private val helper: TaskDbHelper,
) : TaskStore {
    override suspend fun upsert(task: AgentTask) = withContext(Dispatchers.IO) {
        val snapshot = try {
            TaskSnapshotCodec.encode(task)
        } catch (_: Exception) {
            return@withContext
        }
        val values = ContentValues().apply {
            put("task_id", task.taskId)
            put("snapshot_json", snapshot)
            put("status", task.status.name)
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            "agent_tasks",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    override suspend fun listRecent(limit: Int): List<AgentTask> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            """
            SELECT snapshot_json
            FROM agent_tasks
            ORDER BY updated_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(0) ?: continue
                    try {
                        add(TaskSnapshotCodec.decode(raw))
                    } catch (_: Exception) {
                        // Skip corrupt rows.
                    }
                }
            }
        }
    }
}
