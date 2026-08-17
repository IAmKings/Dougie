package com.dougie.core.runtime

import com.dougie.core.llm.FakeLlmProvider
import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.FakeBatteryTool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoopEngineTest {

    @Test
    fun fakeTaskCompletesAfterExactlyThreeToolLoops() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = LoopEngine(
            llm = FakeLlmProvider(),
            tools = mapOf("battery" to FakeBatteryTool()),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val manager = TaskManager(
            loopEngine = engine,
            dispatcher = dispatcher,
            scope = this,
        )

        repeat(3) {
            manager.submit("我现在手机还有多少电？")
            advanceUntilIdle()
            val task = manager.task.value
            assertNotNull(task)
            requireNotNull(task)
            assertEquals(TaskStatus.COMPLETED, task.status)
            assertEquals(3, task.toolTrace.size)
            assertEquals(3, task.loopCount)
            assertTrue(task.toolTrace.all { it.toolName == "battery" })
            assertTrue(task.toolTrace.all { it.status == ToolTraceStatus.SUCCESS })
            assertTrue(task.toolTrace.all { it.resultJson == FakeBatteryTool.STABLE_RESULT })
            assertTrue(task.toolTrace.all { it.toolCallId.isNotBlank() })
            val keys = task.toolTrace.map { task.taskId + it.toolCallId }
            assertEquals(keys.distinct(), keys)
            assertNotNull(task.finalAnswer)
            assertTrue(task.finalAnswer!!.contains("63"))
        }
    }

    @Test
    fun statusSequenceIsPreparingThinkingToolCycleTimesThreeThenCompleted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = LoopEngine(
            llm = FakeLlmProvider(),
            tools = mapOf("battery" to FakeBatteryTool()),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val initial = AgentTask(
            taskId = "task-seq",
            input = "我现在手机还有多少电？",
        )
        val statuses = mutableListOf<TaskStatus>()
        val result = engine.run(initial) { snapshot ->
            if (statuses.lastOrNull() != snapshot.status) {
                statuses += snapshot.status
            }
        }

        assertEquals(TaskStatus.COMPLETED, result.status)
        assertEquals(TaskStatus.PREPARING, statuses.first())
        assertEquals(TaskStatus.COMPLETED, statuses.last())

        val cycle = listOf(
            TaskStatus.THINKING,
            TaskStatus.TOOL_PENDING,
            TaskStatus.TOOL_EXECUTING,
            TaskStatus.TOOL_RESULT,
        )
        var index = statuses.indexOf(TaskStatus.THINKING)
        assertTrue(index >= 0)
        repeat(3) {
            assertEquals(
                "tool cycle starting at $index: $statuses",
                cycle,
                statuses.subList(index, index + cycle.size),
            )
            index += cycle.size
        }
        assertEquals(TaskStatus.THINKING, statuses[index])
        assertEquals(TaskStatus.COMPLETED, statuses[index + 1])
    }

    @Test
    fun cloudProviderBlockedWhenAllowCloudFalse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = false
            var called = false
            override suspend fun generate(context: LoopContext): LlmResponse {
                called = true
                return LlmResponse.FinalAnswer("should-not-appear")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = emptyMap(),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val result = engine.run(AgentTask(taskId = "blocked", input = "电量?")) {}
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.EGRESS_BLOCKED, result.lastError)
        assertEquals(false, provider.called)
    }

    @Test
    fun llmTimeoutFailsTaskWithReadableError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                delay(10_000)
                return LlmResponse.FinalAnswer("late")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = emptyMap(),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            llmTimeoutMs = 50,
        )
        val result = engine.run(AgentTask(taskId = "llm-timeout", input = "电量?")) {}
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.LLM_TIMEOUT, result.lastError)
    }

    @Test
    fun toolTimeoutFailsTaskWithReadableError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.ToolCall(id = "call-1", name = "battery", argsJson = "{}")
            }
        }
        val slowTool = object : AgentTool {
            override val name: String = "battery"
            override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
                delay(10_000)
                return ToolResult(json = "{}")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf("battery" to slowTool),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            toolTimeoutMs = 50,
        )
        val result = engine.run(AgentTask(taskId = "tool-timeout", input = "电量?")) {}
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.TOOL_TIMEOUT, result.lastError)
    }
}
