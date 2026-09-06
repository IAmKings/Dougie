package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectingLlmProviderTest {
    private val context = LoopContext(AgentTask("t", "你好"))

    @Test
    fun cloudConfiguredUsesCloudEvenWhenLocalReady() = runTest {
        val cloud = RecordingProvider("cloud")
        val local = RecordingProvider("local")
        val provider = SelectingLlmProvider(
            cloud = cloud,
            local = local,
            cloudConfigured = { true },
            localReady = { true },
        )
        assertFalse(provider.isLocal)
        assertEquals("cloud", (provider.generate(context) as LlmResponse.FinalAnswer).text)
        assertEquals(1, cloud.calls)
        assertEquals(0, local.calls)
        assertEquals(listOf(LlmEvent.TextDelta("cloud")), provider.stream(context).toList())
        assertEquals(2, cloud.calls)
        assertEquals(0, local.calls)
    }

    @Test
    fun noCloudUsesLocalWhenReady() = runTest {
        val cloud = RecordingProvider("cloud")
        val local = RecordingProvider("local")
        val provider = SelectingLlmProvider(
            cloud = cloud,
            local = local,
            cloudConfigured = { false },
            localReady = { true },
        )
        assertTrue(provider.isLocal)
        assertEquals("local", (provider.generate(context) as LlmResponse.FinalAnswer).text)
        assertEquals(0, cloud.calls)
        assertEquals(1, local.calls)
        assertEquals(listOf(LlmEvent.TextDelta("local")), provider.stream(context).toList())
        assertEquals(0, cloud.calls)
        assertEquals(2, local.calls)
    }

    @Test
    fun noCloudAndNoLocalFallsBackToCloudForGateway() = runTest {
        val cloud = RecordingProvider("cloud")
        val local = RecordingProvider("local")
        val missingLocal = SelectingLlmProvider(
            cloud = cloud,
            local = null,
            cloudConfigured = { false },
            localReady = { true },
        )
        val notReady = SelectingLlmProvider(
            cloud = cloud,
            local = local,
            cloudConfigured = { false },
            localReady = { false },
        )
        assertFalse(missingLocal.isLocal)
        assertFalse(notReady.isLocal)
        assertEquals("cloud", (missingLocal.generate(context) as LlmResponse.FinalAnswer).text)
        assertEquals("cloud", (notReady.generate(context) as LlmResponse.FinalAnswer).text)
        assertEquals(2, cloud.calls)
        assertEquals(0, local.calls)
    }

    @Test
    fun cloudFailureDoesNotRetryLocalInSameCall() = runTest {
        val cloud = RecordingProvider("cloud", fail = true)
        val local = RecordingProvider("local")
        val provider = SelectingLlmProvider(
            cloud = cloud,
            local = local,
            cloudConfigured = { true },
            localReady = { true },
        )
        try {
            provider.generate(context)
            org.junit.Assert.fail("expected cloud failure")
        } catch (e: IllegalStateException) {
            assertEquals("cloud", e.message)
        }
        assertEquals(1, cloud.calls)
        assertEquals(0, local.calls)
        assertFalse(provider.isLocal)
    }

    private class RecordingProvider(
        private val label: String,
        private val fail: Boolean = false,
    ) : LlmProvider {
        var calls: Int = 0

        override val isLocal: Boolean = label == "local"

        override fun stream(context: LoopContext): Flow<LlmEvent> = flow {
            calls += 1
            if (fail) error(label)
            emit(LlmEvent.TextDelta(label))
        }

        override suspend fun generate(context: LoopContext): LlmResponse {
            calls += 1
            if (fail) error(label)
            return LlmResponse.FinalAnswer(label)
        }
    }
}
