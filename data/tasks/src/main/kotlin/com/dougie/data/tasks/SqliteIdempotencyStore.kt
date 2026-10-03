package com.dougie.data.tasks

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.dougie.core.tool.IdempotencyStore

internal class SqliteIdempotencyStore(
    private val helper: TaskDbHelper,
) : IdempotencyStore {
    override fun get(key: String): String? {
        return helper.readableDatabase.rawQuery(
            "SELECT result_json FROM idempotency WHERE idempotency_key = ?",
            arrayOf(key),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    override fun put(key: String, resultJson: String) {
        maybePrune()
        val values = ContentValues().apply {
            put("idempotency_key", key)
            put("result_json", resultJson)
            put("created_at", System.currentTimeMillis())
        }
        helper.writableDatabase.insertWithOnConflict(
            "idempotency",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun maybePrune() {
        val now = System.currentTimeMillis()
        if (now - lastPruneAtMs < PRUNE_INTERVAL_MS) return
        lastPruneAtMs = now
        try {
            helper.writableDatabase.delete(
                "idempotency",
                "created_at < ?",
                arrayOf((now - TTL_MS).toString()),
            )
        } catch (_: Exception) {
            // Hygiene cleanup must never block the tool-call path.
        }
    }

    companion object {
        private const val PRUNE_INTERVAL_MS = 60 * 60 * 1000L
        private const val TTL_MS = 24 * 60 * 60 * 1000L

        @Volatile
        private var lastPruneAtMs = 0L
    }
}
