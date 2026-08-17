package com.dougie.data.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dougie.core.memory.MemoryStore
import com.dougie.core.memory.searchNeedles
import com.dougie.core.model.MemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomMemoryStore(context: Context) : MemoryStore {
    private val helper = MemoryDbHelper(context.applicationContext)

    override suspend fun search(query: String, limit: Int): List<MemoryEntry> = withContext(Dispatchers.IO) {
        val needles = searchNeedles(query)
        if (needles.isEmpty()) return@withContext emptyList()
        val db = helper.readableDatabase
        val found = LinkedHashMap<String, MemoryEntry>()
        for (needle in needles) {
            val match = ftsMatchQuery(needle)
            if (match != null) {
                try {
                    db.rawQuery(
                        """
                        SELECT f.id, f.type, f.content, f.source, f.confidence, f.created_at, f.updated_at
                        FROM memory_facts f
                        JOIN memory_facts_fts fts ON f.docid = fts.rowid
                        WHERE memory_facts_fts MATCH ?
                        ORDER BY f.updated_at DESC
                        LIMIT ?
                        """.trimIndent(),
                        arrayOf(match, limit.toString()),
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val entry = cursor.toEntry()
                            found[entry.id] = entry
                        }
                    }
                } catch (_: Exception) {
                    // LIKE fallback below still runs.
                }
            }
            db.rawQuery(
                """
                SELECT id, type, content, source, confidence, created_at, updated_at
                FROM memory_facts
                WHERE content LIKE '%' || ? || '%'
                ORDER BY updated_at DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(needle, limit.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val entry = cursor.toEntry()
                    found.putIfAbsent(entry.id, entry)
                }
            }
            if (found.size >= limit) break
        }
        found.values.take(limit)
    }

    override suspend fun upsert(entry: MemoryEntry) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = db.rawQuery(
                "SELECT docid FROM memory_facts WHERE id = ?",
                arrayOf(entry.id),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
            val values = android.content.ContentValues().apply {
                put("id", entry.id)
                put("type", entry.type)
                put("content", entry.content)
                put("source", entry.source)
                put("confidence", entry.confidence)
                put("created_at", entry.createdAt)
                put("updated_at", entry.updatedAt)
            }
            val docid = if (existing != null) {
                db.update("memory_facts", values, "id = ?", arrayOf(entry.id))
                db.execSQL("DELETE FROM memory_facts_fts WHERE rowid = ?", arrayOf(existing))
                existing
            } else {
                db.insert("memory_facts", null, values)
            }
            db.execSQL(
                "INSERT INTO memory_facts_fts(rowid, content) VALUES (?, ?)",
                arrayOf(docid, entry.content),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun list(): List<MemoryEntry> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            """
            SELECT id, type, content, source, confidence, created_at, updated_at
            FROM memory_facts
            ORDER BY updated_at DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toEntry())
            }
        }
    }

    override suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val docid = db.rawQuery(
                "SELECT docid FROM memory_facts WHERE id = ?",
                arrayOf(id),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: return@withContext false
            db.execSQL("DELETE FROM memory_facts_fts WHERE rowid = ?", arrayOf(docid))
            val removed = db.delete("memory_facts", "id = ?", arrayOf(id)) > 0
            db.setTransactionSuccessful()
            removed
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM memory_facts_fts")
            db.execSQL("DELETE FROM memory_facts")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun android.database.Cursor.toEntry(): MemoryEntry {
        return MemoryEntry(
            id = getString(getColumnIndexOrThrow("id")),
            type = getString(getColumnIndexOrThrow("type")),
            content = getString(getColumnIndexOrThrow("content")),
            source = getString(getColumnIndexOrThrow("source")),
            confidence = getFloat(getColumnIndexOrThrow("confidence")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        )
    }

    private fun ftsMatchQuery(query: String): String? {
        val cleaned = query.replace(Regex("""["*():^]"""), " ").trim()
        if (cleaned.isEmpty()) return null
        return cleaned
    }
}

internal class MemoryDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DB_NAME,
    null,
    DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE memory_facts (
              docid INTEGER PRIMARY KEY AUTOINCREMENT,
              id TEXT NOT NULL UNIQUE,
              type TEXT NOT NULL,
              content TEXT NOT NULL,
              source TEXT NOT NULL,
              confidence REAL NOT NULL,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE VIRTUAL TABLE memory_facts_fts USING fts4(
              content,
              tokenize=unicode61
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS memory_facts_fts")
        db.execSQL("DROP TABLE IF EXISTS memory_facts")
        onCreate(db)
    }

    companion object {
        const val DB_NAME = "dougie_memory.db"
        const val DB_VERSION = 1
    }
}
