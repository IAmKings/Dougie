package com.dougie.core.memory

import com.dougie.core.model.MemoryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridMemoryStoreTest {
    @Test
    fun notReadyFallsBackToKeywordSearch() = runTest {
        val inner = InMemoryMemoryStore()
        val store = HybridMemoryStore(inner, MapEmbeddingPort(ready = false))
        inner.upsert(fact("a", "我喜欢喝美式"))
        val hits = store.search("美式")
        assertEquals(1, hits.size)
        assertEquals("a", hits.single().id)
        assertTrue(store.search("我平时喝什么咖啡").isEmpty())
    }

    @Test
    fun readyFindsParaphraseAheadOfUnrelatedKeyword() = runTest {
        val inner = InMemoryMemoryStore()
        val embed = MapEmbeddingPort(
            vectors = mapOf(
                "我喜欢喝美式" to floatArrayOf(1f, 0.05f, 0f),
                "我平时喝什么咖啡" to floatArrayOf(0.97f, 0.08f, 0f),
                "现在电量多少" to floatArrayOf(0f, 0f, 1f),
            ),
        )
        val store = HybridMemoryStore(inner, embed)
        store.upsert(fact("coffee", "我喜欢喝美式"))
        store.upsert(fact("battery", "现在电量多少"))
        val hits = store.search("我平时喝什么咖啡")
        assertEquals("coffee", hits.first().id)
        assertEquals("t · 我喜欢喝美式", hits.first().source)
        assertTrue(hits.none { it.id == "battery" })
    }

    @Test
    fun upsertPersistsEmbeddingWhenReady() = runTest {
        val inner = InMemoryMemoryStore()
        val store = HybridMemoryStore(inner, MapEmbeddingPort())
        store.upsert(fact("a", "我喜欢喝美式"))
        assertNotNull(inner.list().single().embedding)
    }

    @Test
    fun upsertSkipsEmbeddingWhenNotReady() = runTest {
        val inner = InMemoryMemoryStore()
        val store = HybridMemoryStore(inner, MapEmbeddingPort(ready = false))
        store.upsert(fact("a", "我喜欢喝美式"))
        assertNull(inner.list().single().embedding)
    }

    @Test
    fun backfillFillsNullEmbeddings() = runTest {
        val inner = InMemoryMemoryStore()
        inner.upsert(fact("a", "我喜欢喝美式"))
        val store = HybridMemoryStore(inner, MapEmbeddingPort())
        assertNull(inner.list().single().embedding)
        store.backfillMissing()
        assertNotNull(inner.list().single().embedding)
        val hits = store.search("我平时喝什么咖啡")
        assertEquals("a", hits.single().id)
    }

    @Test
    fun embedFailureFallsBackToKeyword() = runTest {
        val inner = InMemoryMemoryStore()
        val store = HybridMemoryStore(
            inner,
            MapEmbeddingPort(failQuery = "我平时喝什么咖啡"),
        )
        store.upsert(fact("a", "我喜欢喝美式"))
        assertTrue(store.search("我平时喝什么咖啡").isEmpty())
        assertEquals(1, store.search("美式").size)
    }

    @Test
    fun searchDoesNotAwaitBackfill() = runTest {
        val inner = InMemoryMemoryStore()
        inner.upsert(fact("a", "我喜欢喝美式"))
        val store = HybridMemoryStore(inner, MapEmbeddingPort())
        assertTrue(store.search("我平时喝什么咖啡").isEmpty())
        assertNull(inner.list().single().embedding)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun searchSchedulesIdleBackfillWithoutBlockingRecall() = runTest {
        val inner = InMemoryMemoryStore()
        inner.upsert(fact("a", "我喜欢喝美式"))
        val store = HybridMemoryStore(inner, MapEmbeddingPort(), idleScope = this)
        val first = store.search("美式")
        assertEquals("a", first.single().id)
        assertNull(first.single().embedding)
        advanceUntilIdle()
        assertNotNull(inner.list().single().embedding)
        assertEquals("a", store.search("我平时喝什么咖啡").single().id)
    }

    @Test
    fun throwingEmbedderFallsBackToKeyword() = runTest {
        val inner = InMemoryMemoryStore()
        val store = HybridMemoryStore(inner, ThrowingEmbeddingPort())
        inner.upsert(fact("a", "我喜欢喝美式"))
        val hits = store.search("美式")
        assertEquals(1, hits.size)
        assertEquals("a", hits.single().id)
    }

    @Test
    fun emptyEmbeddingFallsBackToKeyword() = runTest {
        val inner = InMemoryMemoryStore()
        val store = HybridMemoryStore(inner, EmptyEmbeddingPort())
        inner.upsert(fact("a", "我喜欢喝美式"))
        assertEquals("a", store.search("美式").single().id)
        store.upsert(fact("b", "我喜欢喝美式"))
        assertNull(inner.list().first { it.id == "b" }.embedding)
    }

    @Test
    fun searchUsesPreparedQueryAndUpsertUsesPassage() = runTest {
        val inner = InMemoryMemoryStore()
        val port = RecordingEmbeddingPort()
        val store = HybridMemoryStore(inner, port)
        store.upsert(fact("a", "我喜欢喝美式"))
        assertEquals(listOf("我喜欢喝美式"), port.embedded)
        port.embedded.clear()
        store.search("咖啡")
        assertEquals(listOf("query:咖啡"), port.embedded)
    }

    private fun fact(id: String, content: String) = MemoryEntry(
        id = id,
        content = content,
        source = "t · $content",
        confidence = 0.8f,
        createdAt = 1L,
        updatedAt = 1L,
    )
}

private class MapEmbeddingPort(
    private val ready: Boolean = true,
    private val vectors: Map<String, FloatArray> = mapOf(
        "我喜欢喝美式" to floatArrayOf(1f, 0.05f, 0f),
        "我平时喝什么咖啡" to floatArrayOf(0.97f, 0.08f, 0f),
        "现在电量多少" to floatArrayOf(0f, 0f, 1f),
    ),
    private val failQuery: String? = null,
) : EmbeddingPort {
    override fun isReady(): Boolean = ready

    override suspend fun embed(text: String): FloatArray? {
        if (!ready) return null
        if (text == failQuery) return null
        return vectors[text] ?: floatArrayOf(0f, 1f, 0f)
    }
}

private class ThrowingEmbeddingPort : EmbeddingPort {
    override fun isReady(): Boolean = throw IllegalStateException("ready")

    override suspend fun embed(text: String): FloatArray? = throw IllegalStateException("embed")
}

private class EmptyEmbeddingPort : EmbeddingPort {
    override fun isReady(): Boolean = true

    override suspend fun embed(text: String): FloatArray? = floatArrayOf()
}

private class RecordingEmbeddingPort : EmbeddingPort {
    val embedded = mutableListOf<String>()

    override fun isReady(): Boolean = true

    override fun prepareQuery(text: String): String = "query:$text"

    override suspend fun embed(text: String): FloatArray? {
        embedded += text
        return floatArrayOf(1f)
    }
}
