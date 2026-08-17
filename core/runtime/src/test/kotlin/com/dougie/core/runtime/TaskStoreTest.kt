package com.dougie.core.runtime

import com.dougie.core.llm.FakeLlmProvider
import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.FakeBatteryTool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskStoreTest {

    @Test
    fun recoverInterruptedMarksLatestNonTerminalFailed() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "done",
                input = "旧任务",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "live",
                input = "查电量",
                status = TaskStatus.THINKING,
                loopCount = 1,
            ),
        )
        val recovered = recoverInterrupted(store)
        requireNotNull(recovered)
        assertEquals("live", recovered.taskId)
        assertEquals(TaskStatus.FAILED, recovered.status)
        assertEquals(UserFacingErrors.INTERRUPTED, recovered.lastError)
        assertEquals(TaskStatus.FAILED, store.listRecent(1).single().status)
        assertNull(recoverInterrupted(store))
    }

    @Test
    fun recoverInterruptedDoesNotInvokeLlm() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var calls = 0
        val llm = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                calls++
                return LlmResponse.FinalAnswer("不应到达")
            }
        }
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "live",
                input = "查电量",
                status = TaskStatus.THINKING,
            ),
        )
        val recovered = recoverInterrupted(store)
        requireNotNull(recovered)
        val manager = TaskManager(
            loopEngine = LoopEngine(
                llm = llm,
                tools = mapOf("battery" to FakeBatteryTool()),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            ),
            dispatcher = dispatcher,
            scope = this,
            taskStore = store,
        )
        manager.seed(recovered)
        advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals(TaskStatus.FAILED, manager.task.value?.status)
        assertEquals(UserFacingErrors.INTERRUPTED, manager.task.value?.lastError)
    }

    @Test
    fun taskManagerPersistsEveryEmit() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val manager = TaskManager(
            loopEngine = LoopEngine(
                llm = FakeLlmProvider(),
                tools = mapOf("battery" to FakeBatteryTool()),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            ),
            dispatcher = dispatcher,
            scope = this,
            taskStore = store,
        )
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val saved = store.listRecent(1).single()
        assertEquals(TaskStatus.COMPLETED, saved.status)
        assertEquals(3, saved.loopCount)
        assertTrue(saved.toolTrace.all { it.status == ToolTraceStatus.SUCCESS })
    }

    @Test
    fun snapshotRoundTripPreservesTrace() {
        val original = AgentTask(
            taskId = "snap",
            input = "约开会",
            status = TaskStatus.FAILED,
            loopCount = 1,
            lastError = UserFacingErrors.INTERRUPTED,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "c1",
                    toolName = "calendar_create",
                    argsSummary = """{"title":"开会"}""",
                    status = ToolTraceStatus.PENDING,
                ),
            ),
        )
        val restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(original))
        assertEquals(original.taskId, restored.taskId)
        assertEquals(original.input, restored.input)
        assertEquals(original.status, restored.status)
        assertEquals(original.lastError, restored.lastError)
        assertEquals(original.toolTrace.single().toolName, restored.toolTrace.single().toolName)
        assertEquals(original.toolTrace.single().argsSummary, restored.toolTrace.single().argsSummary)
    }
}
