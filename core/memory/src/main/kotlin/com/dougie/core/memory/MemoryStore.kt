package com.dougie.core.memory

import com.dougie.core.model.MemoryEntry

interface MemoryStore {
    suspend fun search(query: String, limit: Int = 5): List<MemoryEntry>
    suspend fun upsert(entry: MemoryEntry)
    suspend fun list(): List<MemoryEntry>
    suspend fun delete(id: String): Boolean
    suspend fun clear()
}
