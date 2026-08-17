package com.dougie.core.memory

import com.dougie.core.model.MemoryEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryMemoryStore : MemoryStore {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, MemoryEntry>()

    override suspend fun search(query: String, limit: Int): List<MemoryEntry> = mutex.withLock {
        val needles = searchNeedles(query)
        if (needles.isEmpty()) return emptyList()
        entries.values
            .filter { entry -> needles.any { needle -> entry.content.contains(needle, ignoreCase = true) } }
            .take(limit)
    }

    override suspend fun upsert(entry: MemoryEntry) {
        mutex.withLock { entries[entry.id] = entry }
    }

    override suspend fun list(): List<MemoryEntry> = mutex.withLock {
        entries.values.sortedByDescending { it.updatedAt }
    }

    override suspend fun delete(id: String): Boolean = mutex.withLock {
        entries.remove(id) != null
    }

    override suspend fun clear() {
        mutex.withLock { entries.clear() }
    }
}

fun searchNeedles(query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parts = Regex("\\s+").split(trimmed).filter { it.isNotBlank() }
    val grams = ArrayList<String>()
    Regex("[\\u4e00-\\u9fff]+").findAll(trimmed).forEach { match ->
        val run = match.value
        if (run.length == 1) {
            grams += run
        } else {
            for (i in 0 until run.length - 1) {
                grams += run.substring(i, i + 2)
            }
        }
    }
    return (parts + trimmed + grams).distinct()
}
