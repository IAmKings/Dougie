package com.dougie.core.runtime

import com.dougie.core.llm.FakeLlmProvider
import com.dougie.core.llm.LlmProvider
import com.dougie.core.memory.InMemoryMemoryStore
import com.dougie.core.memory.MemoryStore
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.MemoryEntry
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.AppIntentTool
import com.dougie.core.tool.CalendarCreateTool
import com.dougie.core.tool.ClipboardReadTool
import com.dougie.core.tool.FakeAppIntentPort
import com.dougie.core.tool.FakeBatteryTool
import com.dougie.core.tool.FakeCalendarPort
import com.dougie.core.tool.FakeClipboardPort
import com.dougie.core.tool.FakeLocationPort
import com.dougie.core.tool.FakeScreenCapturePort
import com.dougie.core.tool.InMemoryScreenFrameStore
import com.dougie.core.tool.LocationTool
import com.dougie.core.tool.ScreenCaptureTool
import com.dougie.core.tool.ScreenMatchTool
import com.dougie.core.tool.SystemTimeTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    @Test
    fun unknownToolFailsWithoutExecuting() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var executed = false
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.ToolCall(id = "call-1", name = "calendar", argsJson = "{}")
            }
        }
        val battery = object : AgentTool {
            override val name: String = "battery"
            override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
                executed = true
                return ToolResult(json = "{}")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf("battery" to battery),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val result = engine.run(AgentTask(taskId = "unknown", input = "日程?")) {}
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.UNKNOWN_TOOL, result.lastError)
        assertEquals(false, executed)
    }

    @Test
    fun extraToolArgsAreStrippedAndDoNotFailTask() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return if (context.task.toolTrace.isEmpty()) {
                    LlmResponse.ToolCall(
                        id = "time-1",
                        name = "time",
                        argsJson = """{"hallucinated":true}""",
                    )
                } else {
                    LlmResponse.FinalAnswer("现在是上午。")
                }
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf("time" to SystemTimeTool()),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val result = engine.run(AgentTask(taskId = "time-task", input = "现在几点了？")) {}
        assertEquals(TaskStatus.COMPLETED, result.status)
        assertEquals(1, result.toolTrace.size)
        assertEquals("{}", result.toolTrace.single().argsSummary)
        assertEquals(ToolTraceStatus.SUCCESS, result.toolTrace.single().status)
        assertTrue(result.finalAnswer!!.contains("现在"))
    }

    @Test
    fun canCallTimeThenBatteryInOneTask() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return when (context.task.toolTrace.size) {
                    0 -> LlmResponse.ToolCall(id = "time-1", name = "time", argsJson = "{}")
                    1 -> LlmResponse.ToolCall(id = "battery-1", name = "battery", argsJson = "{}")
                    else -> LlmResponse.FinalAnswer("现在有电。")
                }
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(
                "time" to SystemTimeTool(),
                "battery" to FakeBatteryTool(),
            ),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val result = engine.run(AgentTask(taskId = "multi", input = "时间和电量")) {}
        assertEquals(TaskStatus.COMPLETED, result.status)
        assertEquals(listOf("time", "battery"), result.toolTrace.map { it.toolName })
        assertTrue(result.toolTrace.all { it.status == ToolTraceStatus.SUCCESS })
        assertTrue(result.toolTrace[0].resultJson!!.contains("iso_local"))
        assertEquals(FakeBatteryTool.STABLE_RESULT, result.toolTrace[1].resultJson)
        assertEquals(2, result.loopCount)
    }

    @Test
    fun emitsStreamingTextWhileThinkingThenClearsOnComplete() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override fun stream(context: LoopContext): Flow<LlmEvent> = flow {
                emit(LlmEvent.TextDelta("你"))
                emit(LlmEvent.TextDelta("好"))
            }
            override suspend fun generate(context: LoopContext): LlmResponse {
                error("stream should be used")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = emptyMap(),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val snapshots = mutableListOf<AgentTask>()
        val result = engine.run(AgentTask(taskId = "stream", input = "你好")) { snapshots += it }
        assertTrue(snapshots.any { it.status == TaskStatus.THINKING && it.streamingText == "你" })
        assertTrue(snapshots.any { it.status == TaskStatus.THINKING && it.streamingText == "你好" })
        assertEquals(TaskStatus.COMPLETED, result.status)
        assertEquals("你好", result.finalAnswer)
        assertEquals(null, result.streamingText)
    }

    @Test
    fun cancelStopsInFlightStreamAndFailsTask() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override fun stream(context: LoopContext): Flow<LlmEvent> = flow {
                emit(LlmEvent.TextDelta("部分"))
                delay(10_000)
                emit(LlmEvent.TextDelta("完成"))
            }
            override suspend fun generate(context: LoopContext): LlmResponse {
                error("stream should be used")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = emptyMap(),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val manager = TaskManager(
            loopEngine = engine,
            dispatcher = dispatcher,
            scope = this,
        )
        manager.submit("你好")
        testScheduler.runCurrent()
        assertEquals("部分", manager.task.value?.streamingText)
        manager.cancel()
        advanceUntilIdle()
        val task = manager.task.value
        assertNotNull(task)
        assertEquals(TaskStatus.FAILED, task!!.status)
        assertEquals(UserFacingErrors.CANCELLED, task.lastError)
        assertEquals(null, task.streamingText)
        assertEquals(null, task.finalAnswer)
    }

    @Test
    fun searchesMemoryBeforeLlmWhenEnabled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryMemoryStore()
        store.upsert(
            MemoryEntry(
                id = "m1",
                content = "我叫小明，住在上海",
                source = "task-0",
                confidence = 0.8f,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        var seenFacts: List<String> = emptyList()
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                seenFacts = context.task.retrievedMemories.map { it.content }
                return LlmResponse.FinalAnswer("你好，小明。")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = emptyMap(),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            memoryStore = store,
            memoryEnabled = { true },
        )
        val result = engine.run(AgentTask(taskId = "mem", input = "我叫什么")) {}
        assertEquals(TaskStatus.COMPLETED, result.status)
        assertEquals(listOf("我叫小明，住在上海"), seenFacts)
        assertEquals(listOf("我叫小明，住在上海"), result.retrievedMemories.map { it.content })
    }

    @Test
    fun skipsMemoryInjectWhenDisabledAndIngestsOnComplete() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryMemoryStore()
        store.upsert(
            MemoryEntry(
                id = "m1",
                content = "我叫小明，住在上海",
                source = "old",
                confidence = 0.8f,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        var enabled = false
        var seenCount = -1
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                seenCount = context.task.retrievedMemories.size
                return LlmResponse.FinalAnswer("记下了。")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = emptyMap(),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            memoryStore = store,
            memoryEnabled = { enabled },
        )
        val skipped = engine.run(AgentTask(taskId = "skip", input = "我叫什么")) {}
        assertEquals(0, seenCount)
        assertTrue(skipped.retrievedMemories.isEmpty())

        enabled = true
        val ingested = engine.run(AgentTask(taskId = "new", input = "我喜欢喝茶")) {}
        assertEquals(TaskStatus.COMPLETED, ingested.status)
        assertTrue(store.list().any { it.content == "我喜欢喝茶" && it.source.contains("new") })
    }

    @Test
    fun ingestFailureDoesNotFailCompletedTask() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = object : MemoryStore {
            override suspend fun search(query: String, limit: Int) = emptyList<MemoryEntry>()
            override suspend fun upsert(entry: MemoryEntry) = throw IllegalStateException("disk")
            override suspend fun list() = emptyList<MemoryEntry>()
            override suspend fun delete(id: String) = false
            override suspend fun clear() = Unit
        }
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.FinalAnswer("好的")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = emptyMap(),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            memoryStore = store,
            memoryEnabled = { true },
        )
        val result = engine.run(AgentTask(taskId = "ok", input = "我叫小明")) {}
        assertEquals(TaskStatus.COMPLETED, result.status)
        assertEquals("好的", result.finalAnswer)
    }

    @Test
    fun l2ConfirmExecutesOnceAndRejectSkipsExecute() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeCalendarPort()
        val create = CalendarCreateTool(port)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return if (context.task.toolTrace.isEmpty()) {
                    LlmResponse.ToolCall(
                        id = "cal-1",
                        name = CalendarCreateTool.NAME,
                        argsJson = """{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}""",
                    )
                } else {
                    LlmResponse.FinalAnswer("已创建日程。")
                }
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(CalendarCreateTool.NAME to create),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val confirmed = engine.run(AgentTask(taskId = "c1", input = "约开会")) { snapshot ->
            if (snapshot.status == TaskStatus.AWAITING_CONFIRMATION) engine.confirm()
        }
        assertEquals(TaskStatus.COMPLETED, confirmed.status)
        assertEquals(1, port.createCalls.size)

        val rejectedPort = FakeCalendarPort()
        val rejectEngine = LoopEngine(
            llm = provider,
            tools = mapOf(CalendarCreateTool.NAME to CalendarCreateTool(rejectedPort)),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val rejected = rejectEngine.run(AgentTask(taskId = "c2", input = "约开会")) { snapshot ->
            if (snapshot.status == TaskStatus.AWAITING_CONFIRMATION) rejectEngine.reject()
        }
        assertEquals(TaskStatus.FAILED, rejected.status)
        assertEquals(UserFacingErrors.CONFIRM_REJECTED, rejected.lastError)
        assertEquals(0, rejectedPort.createCalls.size)
    }

    @Test
    fun l2AppIntentConfirmLaunchesOnceAndRejectSkips() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeAppIntentPort()
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return if (context.task.toolTrace.isEmpty()) {
                    LlmResponse.ToolCall(
                        id = "intent-1",
                        name = AppIntentTool.NAME,
                        argsJson = """{"uri":"https://example.com"}""",
                    )
                } else {
                    LlmResponse.FinalAnswer("已打开链接。")
                }
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(AppIntentTool.NAME to AppIntentTool(port)),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val confirmed = engine.run(AgentTask(taskId = "i1", input = "打开网页")) { snapshot ->
            if (snapshot.status == TaskStatus.AWAITING_CONFIRMATION) engine.confirm()
        }
        assertEquals(TaskStatus.COMPLETED, confirmed.status)
        assertEquals(1, port.launchCount)

        val rejectedPort = FakeAppIntentPort()
        val rejectEngine = LoopEngine(
            llm = provider,
            tools = mapOf(AppIntentTool.NAME to AppIntentTool(rejectedPort)),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val rejected = rejectEngine.run(AgentTask(taskId = "i2", input = "打开网页")) { snapshot ->
            if (snapshot.status == TaskStatus.AWAITING_CONFIRMATION) rejectEngine.reject()
        }
        assertEquals(TaskStatus.FAILED, rejected.status)
        assertEquals(UserFacingErrors.CONFIRM_REJECTED, rejected.lastError)
        assertEquals(0, rejectedPort.launchCount)
    }

    @Test
    fun disallowedAppIntentSchemeFailsBeforeConfirm() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schemes = listOf("tel:123456", "file:///sdcard/secret.txt")
        for (uri in schemes) {
            val port = FakeAppIntentPort()
            val provider = object : LlmProvider {
                override val isLocal: Boolean = true
                override suspend fun generate(context: LoopContext): LlmResponse {
                    return LlmResponse.ToolCall(
                        id = "intent-bad",
                        name = AppIntentTool.NAME,
                        argsJson = """{"uri":"$uri"}""",
                    )
                }
            }
            val engine = LoopEngine(
                llm = provider,
                tools = mapOf(AppIntentTool.NAME to AppIntentTool(port)),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            )
            val statuses = mutableListOf<TaskStatus>()
            val result = engine.run(AgentTask(taskId = "deny-$uri", input = "打开")) { snapshot ->
                statuses += snapshot.status
            }
            assertEquals(uri, TaskStatus.FAILED, result.status)
            assertEquals(uri, UserFacingErrors.APP_INTENT_DENIED, result.lastError)
            assertEquals(uri, 0, port.launchCount)
            assertEquals(uri, false, statuses.contains(TaskStatus.AWAITING_CONFIRMATION))
        }
    }

    @Test
    fun invalidL2ArgsFailBeforeConfirmOrExecute() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeCalendarPort()
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.ToolCall(
                    id = "cal-bad",
                    name = CalendarCreateTool.NAME,
                    argsJson = """{"title":"开会"}""",
                )
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(CalendarCreateTool.NAME to CalendarCreateTool(port)),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val statuses = mutableListOf<TaskStatus>()
        val result = engine.run(AgentTask(taskId = "bad", input = "约开会")) { snapshot ->
            statuses += snapshot.status
        }
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, result.lastError)
        assertEquals(0, port.createCalls.size)
        assertEquals(false, statuses.contains(TaskStatus.AWAITING_CONFIRMATION))
    }

    @Test
    fun missingPermissionDoesNotExecute() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeCalendarPort()
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.ToolCall(
                    id = "cal-1",
                    name = CalendarCreateTool.NAME,
                    argsJson = """{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}""",
                )
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(CalendarCreateTool.NAME to CalendarCreateTool(port)),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            policyEngine = PolicyEngine { false },
        )
        val result = engine.run(AgentTask(taskId = "deny", input = "约开会")) {}
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.PERMISSION_DENIED, result.lastError)
        assertEquals(0, port.createCalls.size)
    }

    @Test
    fun locationMissingPermissionDoesNotExecute() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeLocationPort()
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.ToolCall(id = "loc-1", name = LocationTool.NAME, argsJson = "{}")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(LocationTool.NAME to LocationTool(port)),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            policyEngine = PolicyEngine { false },
        )
        val result = engine.run(AgentTask(taskId = "loc-deny", input = "我在哪")) {}
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.PERMISSION_DENIED, result.lastError)
        assertEquals(0, port.queryCount)
    }

    @Test
    fun screenCaptureResultIsMetadataOnly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeScreenCapturePort()
        val store = InMemoryScreenFrameStore()
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return if (context.task.toolTrace.isEmpty()) {
                    LlmResponse.ToolCall(id = "cap-1", name = ScreenCaptureTool.NAME, argsJson = "{}")
                } else {
                    LlmResponse.FinalAnswer("已截取")
                }
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(ScreenCaptureTool.NAME to ScreenCaptureTool(port, store)),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val result = engine.run(AgentTask(taskId = "cap", input = "截屏")) {}
        assertEquals(TaskStatus.COMPLETED, result.status)
        val json = result.toolTrace.single().resultJson.orEmpty()
        assertTrue(json.contains("\"capture_id\""))
        assertTrue(json.contains("\"width\""))
        assertTrue(json.contains("\"height\""))
        assertTrue(!json.contains("data:image"))
        assertTrue(!json.contains("base64"))
        assertEquals("synthetic", store.last()?.id)
    }

    @Test
    fun screenMatchWithoutFrameFailsWithoutGuessing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.ToolCall(
                    id = "match-1",
                    name = ScreenMatchTool.NAME,
                    argsJson = """{"template_id":"solid"}""",
                )
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(ScreenMatchTool.NAME to ScreenMatchTool(InMemoryScreenFrameStore())),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val result = engine.run(AgentTask(taskId = "match", input = "屏幕上有标志吗")) {}
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.SCREEN_MATCH_FAILED, result.lastError)
        assertTrue(result.toolTrace.single().resultJson.orEmpty().contains("\"found\":false"))
    }

    @Test
    fun clipboardBackgroundDoesNotExecuteAsSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeClipboardPort(foreground = false, text = "secret")
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.ToolCall(
                    id = "clip-1",
                    name = ClipboardReadTool.NAME,
                    argsJson = "{}",
                )
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = mapOf(ClipboardReadTool.NAME to ClipboardReadTool(port)),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        val result = engine.run(AgentTask(taskId = "bg", input = "剪贴板里有什么")) {}
        assertEquals(TaskStatus.FAILED, result.status)
        assertEquals(UserFacingErrors.CLIPBOARD_NOT_FOREGROUND, result.lastError)
        assertEquals(ToolTraceStatus.FAILED, result.toolTrace.single().status)
        assertEquals(false, result.toolTrace.single().resultJson.orEmpty().contains("secret"))
    }

    @Test
    fun calendarCreateSameKeyInsertsOnceViaTool() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val port = FakeCalendarPort()
        val tool = CalendarCreateTool(port)
        val ctx = ToolContext(taskId = "task-1", toolCallId = "call-1")
        val args = """{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}"""
        tool.execute(args, ctx)
        tool.execute(args, ctx)
        assertEquals(1, port.createCalls.size)
        val engine = LoopEngine(
            llm = FakeLlmProvider(),
            tools = mapOf("battery" to FakeBatteryTool()),
            dispatcher = dispatcher,
            stepDelayMs = 0,
        )
        assertEquals(TaskStatus.COMPLETED, engine.run(AgentTask(taskId = "keep-l0", input = "电量")) {}.status)
    }

    @Test
    fun auditRecordsTaskIdToolNameAndOutcomeOnly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recorded = mutableListOf<Triple<String, String, String>>()
        val engine = LoopEngine(
            llm = FakeLlmProvider(),
            tools = mapOf("battery" to FakeBatteryTool()),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            auditLog = AuditLog { taskId, toolName, outcome ->
                recorded += Triple(taskId, toolName, outcome)
            },
        )
        val result = engine.run(AgentTask(taskId = "aud", input = "电量")) {}
        assertEquals(TaskStatus.COMPLETED, result.status)
        assertEquals(3, recorded.size)
        assertTrue(recorded.all { it.first == "aud" && it.second == "battery" && it.third == "SUCCESS" })
    }

    @Test
    fun memorySearchCancellationIsNotSwallowed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = object : MemoryStore {
            override suspend fun search(query: String, limit: Int): List<MemoryEntry> {
                throw CancellationException("search cancelled")
            }
            override suspend fun upsert(entry: MemoryEntry) = Unit
            override suspend fun list() = emptyList<MemoryEntry>()
            override suspend fun delete(id: String) = false
            override suspend fun clear() = Unit
        }
        val provider = object : LlmProvider {
            override val isLocal: Boolean = true
            override suspend fun generate(context: LoopContext): LlmResponse {
                return LlmResponse.FinalAnswer("不应到达")
            }
        }
        val engine = LoopEngine(
            llm = provider,
            tools = emptyMap(),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            memoryStore = store,
            memoryEnabled = { true },
        )
        try {
            engine.run(AgentTask(taskId = "c", input = "我叫什么")) {}
            throw AssertionError("expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("search cancelled", e.message)
        }
    }
}
