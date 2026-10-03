package com.dougie.core.runtime

import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskManagerTerminalStateTest {

    @Test
    fun cancelledTaskPersistsTerminalFailedRow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = HangingUpsertStore()
        val gate = CompletableDeferred<Unit>()
        val llm = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                gate.await()
                return LlmResponse.FinalAnswer("不应到达")
            }
        }
        val manager = TaskManager(
            loopEngine = LoopEngine(
                llm = llm,
                tools = emptyMap(),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            ),
            dispatcher = dispatcher,
            taskStore = store,
        )
        manager.submit("测试取消")
        // runCurrent, not advanceUntilIdle: the loop wraps the LLM call in withTimeout, so
        // advancing virtual time would hit LLM_TIMEOUT before cancel ever runs.
        runCurrent()
        assertEquals(TaskStatus.THINKING, manager.task.value?.status)

        manager.cancel()
        runCurrent()

        val row = store.listRecent(1).single()
        assertEquals(TaskStatus.FAILED, row.status)
        assertEquals(UserFacingErrors.CANCELLED, row.lastError)
        assertEquals(null, row.confirmDeadlineAt)
    }

    @Test
    fun streamingEmitsAreThrottledButTerminalAlwaysPersists() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = CountingStore()
        val llm = object : LlmProvider {
            override val isLocal: Boolean = true
            override fun stream(context: LoopContext): Flow<LlmEvent> = flow {
                repeat(60) { emit(LlmEvent.TextDelta("字")) }
            }
            override suspend fun generate(context: LoopContext): LlmResponse {
                throw AssertionError("stream is overridden; generate must not run")
            }
        }
        val manager = TaskManager(
            loopEngine = LoopEngine(
                llm = llm,
                tools = emptyMap(),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            ),
            dispatcher = dispatcher,
            taskStore = store,
            persistIntervalMs = Long.MAX_VALUE,
        )
        manager.submit("流式回答")
        advanceUntilIdle()

        assertEquals(TaskStatus.COMPLETED, manager.task.value?.status)
        assertTrue("throttled upserts should stay constant, got ${store.upsertCount}", store.upsertCount <= 3)
        assertEquals(TaskStatus.COMPLETED, store.delegate.listRecent(1).single().status)
    }

    @Test
    fun zeroPersistIntervalKeepsLegacyEveryEmitBehavior() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = CountingStore()
        val llm = object : LlmProvider {
            override val isLocal: Boolean = true
            override fun stream(context: LoopContext): Flow<LlmEvent> = flow {
                repeat(60) { emit(LlmEvent.TextDelta("字")) }
            }
            override suspend fun generate(context: LoopContext): LlmResponse {
                throw AssertionError("stream is overridden; generate must not run")
            }
        }
        val manager = TaskManager(
            loopEngine = LoopEngine(
                llm = llm,
                tools = emptyMap(),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            ),
            dispatcher = dispatcher,
            taskStore = store,
            persistIntervalMs = 0L,
        )
        manager.submit("流式回答")
        advanceUntilIdle()

        assertEquals(TaskStatus.COMPLETED, manager.task.value?.status)
        assertTrue("every emit should persist, got ${store.upsertCount}", store.upsertCount >= 62)
    }

    /**
     * The yield() suspension point inside upsert throws CancellationException when the
     * calling coroutine is already cancelled — reproducing the shape of the SQLite store
     * (withContext(Dispatchers.IO)) that made cancelled terminal states vanish.
     */
    private class HangingUpsertStore : TaskStore {
        private val delegate = InMemoryTaskStore()

        override suspend fun upsert(task: AgentTask) {
            yield()
            delegate.upsert(task)
        }

        override suspend fun listRecent(limit: Int): List<AgentTask> = delegate.listRecent(limit)
        override suspend fun listByConversation(conversationId: String): List<AgentTask> =
            delegate.listByConversation(conversationId)

        override suspend fun deleteByConversation(conversationId: String): Int =
            delegate.deleteByConversation(conversationId)

        override suspend fun deleteByTaskId(taskId: String): Int = delegate.deleteByTaskId(taskId)
        override suspend fun searchCompletedTurns(
            query: String,
            excludeTaskIds: Set<String>,
            limit: Int,
        ): List<AgentTask> = delegate.searchCompletedTurns(query, excludeTaskIds, limit)

        override suspend fun searchHistory(query: String, limit: Int): List<AgentTask> =
            delegate.searchHistory(query, limit)
    }

    private class CountingStore : TaskStore {
        val delegate = InMemoryTaskStore()

        var upsertCount = 0
            private set

        override suspend fun upsert(task: AgentTask) {
            delegate.upsert(task)
            upsertCount += 1
        }

        override suspend fun listRecent(limit: Int): List<AgentTask> = delegate.listRecent(limit)
        override suspend fun listByConversation(conversationId: String): List<AgentTask> =
            delegate.listByConversation(conversationId)

        override suspend fun deleteByConversation(conversationId: String): Int =
            delegate.deleteByConversation(conversationId)

        override suspend fun deleteByTaskId(taskId: String): Int = delegate.deleteByTaskId(taskId)
        override suspend fun searchCompletedTurns(
            query: String,
            excludeTaskIds: Set<String>,
            limit: Int,
        ): List<AgentTask> = delegate.searchCompletedTurns(query, excludeTaskIds, limit)

        override suspend fun searchHistory(query: String, limit: Int): List<AgentTask> =
            delegate.searchHistory(query, limit)
    }
}
