package com.dougie.core.memory

import com.dougie.core.model.MemoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class HybridMemoryStore(
    private val inner: MemoryStore,
    private val embedding: EmbeddingPort,
    private val minCosine: Float = DEFAULT_MIN_COSINE,
    private val idleScope: CoroutineScope? = null,
) : MemoryStore {
    private val backfillLock = Mutex()

    override suspend fun search(query: String, limit: Int): List<MemoryEntry> {
        val keyword = inner.search(query, limit)
        if (!readyOrFalse()) return keyword
        val queryVec = embedOrNull(query) ?: return keyword
        val entries = try {
            inner.list()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return keyword
        }
        if (idleScope != null && entries.any { it.embedding == null }) {
            idleScope.launch { backfillMissing() }
        }
        val semantic = entries
            .mapNotNull { entry ->
                val vec = littleEndianToFloats(entry.embedding) ?: return@mapNotNull null
                val score = cosineSimilarity(queryVec, vec)
                if (score >= minCosine) score to entry else null
            }
            .sortedByDescending { it.first }
            .map { it.second }
        val merged = LinkedHashMap<String, MemoryEntry>()
        for (entry in semantic) {
            if (merged.size >= limit) break
            merged[entry.id] = entry
        }
        for (entry in keyword) {
            if (merged.size >= limit) break
            merged.putIfAbsent(entry.id, entry)
        }
        return merged.values.toList()
    }

    override suspend fun upsert(entry: MemoryEntry) {
        inner.upsert(withEmbedding(entry))
    }

    override suspend fun list(): List<MemoryEntry> = inner.list()

    override suspend fun delete(id: String): Boolean = inner.delete(id)

    override suspend fun clear() = inner.clear()

    suspend fun backfillMissing() {
        if (!readyOrFalse()) return
        backfillLock.withLock {
            if (!readyOrFalse()) return
            for (entry in inner.list()) {
                if (entry.embedding != null) continue
                try {
                    val filled = withEmbedding(entry)
                    if (filled.embedding != null) inner.upsert(filled)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Skip this row; remaining facts still backfill.
                }
            }
        }
    }

    private suspend fun withEmbedding(entry: MemoryEntry): MemoryEntry {
        if (!readyOrFalse()) return entry
        val vec = embedOrNull(entry.content) ?: return entry
        return entry.copy(embedding = floatsToLittleEndian(vec))
    }

    private fun readyOrFalse(): Boolean = try {
        embedding.isReady()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private suspend fun embedOrNull(text: String): FloatArray? = try {
        embedding.embed(text)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
