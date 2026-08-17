package com.dougie.data.tasks

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class TaskDbHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE agent_tasks (
              task_id TEXT PRIMARY KEY NOT NULL,
              snapshot_json TEXT NOT NULL,
              status TEXT NOT NULL,
              updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE idempotency (
              idempotency_key TEXT PRIMARY KEY NOT NULL,
              result_json TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE audit_log (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              task_id TEXT NOT NULL,
              tool_name TEXT NOT NULL,
              outcome TEXT NOT NULL,
              created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS audit_log")
        db.execSQL("DROP TABLE IF EXISTS idempotency")
        db.execSQL("DROP TABLE IF EXISTS agent_tasks")
        onCreate(db)
    }

    companion object {
        const val DB_NAME = "dougie_tasks.db"
        const val DB_VERSION = 1
    }
}
