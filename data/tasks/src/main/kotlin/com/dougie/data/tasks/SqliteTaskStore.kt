package com.dougie.data.tasks

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.dougie.core.model.AgentTask
import com.dougie.core.model.ConversationIds
import com.dougie.core.runtime.TaskSnapshotCodec
import com.dougie.core.runtime.TaskStore
import com.dougie.core.runtime.conversationSearchNeedles
import com.dougie.core.runtime.matchesCompletedTurnNeedles
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

    override suspend fun listByConversation(conversationId: String): List<AgentTask> =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.rawQuery(
                """
                SELECT snapshot_json
                FROM agent_tasks
                ORDER BY updated_at ASC
                """.trimIndent(),
                emptyArray(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val raw = cursor.getString(0) ?: continue
                        try {
                            val task = TaskSnapshotCodec.decode(raw)
                            if (task.conversationId == conversationId) add(task)
                        } catch (_: Exception) {
                            // Skip corrupt rows.
                        }
                    }
                }
            }
        }

    override suspend fun deleteByConversation(conversationId: String): Int =
        withContext(Dispatchers.IO) {
            if (conversationId.isBlank() || conversationId == ConversationIds.DEFAULT) {
                return@withContext 0
            }
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val ids = buildList {
                    db.rawQuery(
                        """
                        SELECT task_id, snapshot_json
                        FROM agent_tasks
                        """.trimIndent(),
                        emptyArray(),
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val taskId = cursor.getString(0) ?: continue
                            val raw = cursor.getString(1) ?: continue
                            try {
                                val task = TaskSnapshotCodec.decode(raw)
                                if (task.conversationId == conversationId) add(taskId)
                            } catch (_: Exception) {
                                // Skip corrupt rows.
                            }
                        }
                    }
                }
                if (ids.isEmpty()) {
                    db.setTransactionSuccessful()
                    return@withContext 0
                }
                val placeholders = ids.joinToString(",") { "?" }
                val deleted = db.delete(
                    "agent_tasks",
                    "task_id IN ($placeholders)",
                    ids.toTypedArray(),
                )
                db.setTransactionSuccessful()
                deleted
            } finally {
                db.endTransaction()
            }
        }

    override suspend fun searchCompletedTurns(
        query: String,
        excludeTaskIds: Set<String>,
        limit: Int,
    ): List<AgentTask> = withContext(Dispatchers.IO) {
        val needles = conversationSearchNeedles(query)
        if (needles.isEmpty() || limit <= 0) return@withContext emptyList()
        helper.readableDatabase.rawQuery(
            """
            SELECT task_id, snapshot_json
            FROM agent_tasks
            ORDER BY updated_at DESC
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < limit) {
                    val raw = cursor.getString(1) ?: continue
                    try {
                        val task = TaskSnapshotCodec.decode(raw)
                        if (task.matchesCompletedTurnNeedles(needles, excludeTaskIds)) {
                            add(task)
                        }
                    } catch (_: Exception) {
                        // Skip corrupt rows.
                    }
                }
            }
        }
    }
}
