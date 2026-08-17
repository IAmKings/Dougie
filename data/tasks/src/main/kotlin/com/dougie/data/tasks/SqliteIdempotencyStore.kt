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
        val values = ContentValues().apply {
            put("idempotency_key", key)
            put("result_json", resultJson)
        }
        helper.writableDatabase.insertWithOnConflict(
            "idempotency",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }
}
