package com.dougie.core.memory

import com.dougie.core.model.GateResult
import com.dougie.core.model.MemoryEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGateTest {
    @Test
    fun searchHitsXiaomingAndShanghaiWithSource() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { true })
        val result = gate.ingest("我叫小明，住在上海", assistantText = "好的，小明。", sourceTaskId = "task-1")
        assertTrue(result is GateResult.Stored)
        val stored = (result as GateResult.Stored).entry
        assertTrue(stored.source.contains("task-1"))

        val byName = store.search("小明")
        assertEquals(1, byName.size)
        assertTrue(byName.single().content.contains("小明"))
        assertTrue(byName.single().source.contains("task-1"))

        val byCity = store.search("上海")
        assertEquals(1, byCity.size)
        assertEquals(stored.id, byCity.single().id)
    }

    @Test
    fun rejectsApiKeyPrefix() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { true })
        val result = gate.ingest("我的密钥是 sk-abc123", assistantText = null, sourceTaskId = "t")
        assertEquals(GateResult.SkippedSensitive, result)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun rejectsPasswordPhrase() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { true })
        val result = gate.ingest("我叫小明，密码是 secret", assistantText = null, sourceTaskId = "t")
        assertEquals(GateResult.SkippedSensitive, result)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun rejectsScreenshotPayload() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { true })
        val result = gate.ingest(
            "我叫小明，住在上海",
            assistantText = "data:image/png;base64,AAAA",
            sourceTaskId = "t",
        )
        assertEquals(GateResult.SkippedSensitive, result)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun disabledIngestIsNoOp() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { false })
        val result = gate.ingest("我叫小明，住在上海", assistantText = "好的", sourceTaskId = "t")
        assertEquals(GateResult.SkippedDisabled, result)
        assertTrue(store.list().isEmpty())
        store.upsert(
            MemoryEntry(
                id = "existing",
                content = "我叫小明，住在上海",
                source = "manual",
                confidence = 1f,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        assertEquals(1, store.search("小明").size)
    }

    @Test
    fun skipsWhenUserAsksNotToRemember() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { true })
        val result = gate.ingest("我叫小明，不要记住", assistantText = null, sourceTaskId = "t")
        assertEquals(GateResult.SkippedNoFact, result)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun skipsDuplicateFact() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { true })
        val first = gate.ingest("我叫小明，住在上海", assistantText = null, sourceTaskId = "t1")
        assertTrue(first is GateResult.Stored)
        val second = gate.ingest("我叫小明，住在上海", assistantText = null, sourceTaskId = "t2")
        assertEquals(GateResult.SkippedDuplicate, second)
        assertEquals(1, store.list().size)
    }

    @Test
    fun skipsBatteryQuestionWithoutFact() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { true })
        val result = gate.ingest("我现在手机还有多少电？", assistantText = "63%", sourceTaskId = "t")
        assertEquals(GateResult.SkippedNoFact, result)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun skipsWhereDoILiveQuestion() = runTest {
        val store = InMemoryMemoryStore()
        val gate = MemoryGate(store, enabled = { true })
        val result = gate.ingest("我住在哪里", assistantText = "上海", sourceTaskId = "t")
        assertEquals(GateResult.SkippedNoFact, result)
        assertTrue(store.list().isEmpty())
    }
}
