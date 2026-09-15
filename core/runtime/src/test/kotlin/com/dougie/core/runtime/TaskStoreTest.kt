package com.dougie.core.runtime

import com.dougie.core.llm.FakeLlmProvider
import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.AgentTask
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentMeta
import com.dougie.core.model.CompletionPath
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.ConversationTurn
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.FakeBatteryTool
import com.dougie.core.tool.InMemoryScreenFrameStore
import com.dougie.core.tool.whiteSquareOnBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
                startedAt = 1_000L,
                conversationId = "other-thread",
            ),
        )
        val recovered = recoverInterrupted(store)
        requireNotNull(recovered)
        assertEquals("live", recovered.taskId)
        assertEquals(TaskStatus.FAILED, recovered.status)
        assertEquals(UserFacingErrors.INTERRUPTED, recovered.lastError)
        assertEquals("other-thread", recovered.conversationId)
        assertEquals(1_000L, recovered.startedAt)
        val recoveredEnded = recovered.endedAt
        assertTrue(recoveredEnded != null && recoveredEnded >= 1_000L)
        assertEquals(TaskStatus.FAILED, store.listRecent(1).single().status)
        assertEquals(recoveredEnded, store.listRecent(1).single().endedAt)
        assertNull(recoverInterrupted(store))
    }

    @Test
    fun stampEndedAtIfTerminalIsIdempotentAndSkipsNonTerminal() {
        val failed = AgentTask(
            taskId = "t",
            input = "查电量",
            status = TaskStatus.FAILED,
            startedAt = 1L,
            endedAt = 2L,
        )
        assertEquals(2L, failed.stampEndedAtIfTerminal(nowMs = 99L).endedAt)
        val thinking = AgentTask(
            taskId = "live",
            input = "查电量",
            status = TaskStatus.THINKING,
            startedAt = 1L,
        )
        assertNull(thinking.stampEndedAtIfTerminal(nowMs = 99L).endedAt)
        val completed = AgentTask(
            taskId = "done",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            startedAt = 1L,
        )
        assertEquals(99L, completed.stampEndedAtIfTerminal(nowMs = 99L).endedAt)
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
        val startedAt = saved.startedAt
        val endedAt = saved.endedAt
        assertTrue(startedAt != null)
        assertTrue(endedAt != null && startedAt != null && endedAt >= startedAt)
        assertEquals(startedAt, manager.task.value?.startedAt)
        assertEquals(endedAt, manager.task.value?.endedAt)
    }

    @Test
    fun submitSetsStartedAtBeforeLoop() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = TaskManager(
            loopEngine = LoopEngine(
                llm = FakeLlmProvider(),
                tools = mapOf("battery" to FakeBatteryTool()),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            ),
            dispatcher = dispatcher,
            scope = this,
        )
        manager.submit("我现在手机还有多少电？")
        val live = manager.task.value
        requireNotNull(live)
        assertTrue(live.startedAt != null)
        assertNull(live.endedAt)
        advanceUntilIdle()
        val done = manager.task.value
        requireNotNull(done)
        assertEquals(TaskStatus.COMPLETED, done.status)
        assertTrue(done.endedAt != null && done.startedAt != null && done.endedAt!! >= done.startedAt!!)
    }

    @Test
    fun cancelPersistsEndedAtOnFailedSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
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
        val manager = TaskManager(
            loopEngine = LoopEngine(
                llm = provider,
                tools = emptyMap(),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            ),
            dispatcher = dispatcher,
            scope = this,
            taskStore = store,
        )
        manager.submit("你好")
        testScheduler.runCurrent()
        manager.cancel()
        advanceUntilIdle()
        val saved = store.listRecent(1).single()
        assertEquals(TaskStatus.FAILED, saved.status)
        assertEquals(UserFacingErrors.CANCELLED, saved.lastError)
        val startedAt = saved.startedAt
        val endedAt = saved.endedAt
        assertTrue(startedAt != null)
        assertTrue(endedAt != null && startedAt != null && endedAt >= startedAt)
    }

    @Test
    fun submitClearsPinWhenTaskCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val frames = InMemoryScreenFrameStore()
        frames.put(whiteSquareOnBlack())
        frames.pin()
        val manager = TaskManager(
            loopEngine = LoopEngine(
                llm = FakeLlmProvider(),
                tools = mapOf("battery" to FakeBatteryTool()),
                dispatcher = dispatcher,
                stepDelayMs = 0,
            ),
            dispatcher = dispatcher,
            scope = this,
            screenFrames = frames,
        )
        manager.submit("我现在手机还有多少电？", "synthetic", 32, 32)
        advanceUntilIdle()
        assertNull(frames.pinned())
        assertEquals("synthetic", frames.last()?.id)
        assertEquals("synthetic", manager.task.value?.attachedCaptureId)
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
        assertNull(restored.completionPath)
        assertNull(restored.startedAt)
        assertNull(restored.endedAt)
    }

    @Test
    fun snapshotRoundTripPreservesAttachedScreenMetadata() {
        val original = AgentTask(
            taskId = "snap-cap",
            input = "看屏幕",
            attachedCaptureId = "cap1",
            attachedWidth = 720,
            attachedHeight = 1584,
        )
        val restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(original))
        assertEquals("cap1", restored.attachedCaptureId)
        assertEquals(720, restored.attachedWidth)
        assertEquals(1584, restored.attachedHeight)
        val encoded = TaskSnapshotCodec.encode(original)
        assertTrue(!encoded.contains("gray"))
        assertTrue(!encoded.contains("base64"))
    }

    @Test
    fun snapshotRoundTripPreservesAttachmentsWithoutPixels() {
        val original = AgentTask(
            taskId = "snap-att",
            input = "看这些图",
            attachedCaptureId = "cap1",
            attachedWidth = 720,
            attachedHeight = 1584,
            attachments = listOf(
                AttachmentMeta("cap1", AttachmentKind.SCREEN, 720, 1584),
                AttachmentMeta("g1", AttachmentKind.GALLERY, 800, 600),
            ),
        )
        val restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(original))
        assertEquals(2, restored.attachments.size)
        assertEquals(AttachmentKind.SCREEN, restored.attachments[0].kind)
        assertEquals("g1", restored.attachments[1].id)
        val encoded = TaskSnapshotCodec.encode(original)
        assertTrue(!encoded.contains("gray"))
        assertTrue(!encoded.contains("base64"))
        assertTrue(!encoded.contains("data:image"))
    }

    @Test
    fun snapshotDecodeWithoutAttachedFieldsStaysNull() {
        val restored = TaskSnapshotCodec.decode(
            TaskSnapshotCodec.encode(AgentTask(taskId = "old", input = "查电量")),
        )
        assertNull(restored.attachedCaptureId)
        assertNull(restored.attachedWidth)
        assertNull(restored.attachedHeight)
        assertEquals(false, restored.speakReply)
        assertNull(restored.completionPath)
        assertNull(restored.startedAt)
        assertNull(restored.endedAt)
    }

    @Test
    fun snapshotDecodeWithoutSpeakReplyDefaultsFalse() {
        val restored = TaskSnapshotCodec.decode(
            """{"taskId":"old","input":"查电量","status":"COMPLETED","loopCount":0,"maxLoops":8,"toolTrace":[],"finalAnswer":"好了","lastError":null,"streamingText":null,"retrievedMemories":[],"attachments":[]}""",
        )
        assertEquals(false, restored.speakReply)
        assertEquals("好了", restored.finalAnswer)
        assertNull(restored.completionPath)
        assertNull(restored.startedAt)
        assertNull(restored.endedAt)
    }

    @Test
    fun snapshotRoundTripPreservesCompletionPath() {
        val original = AgentTask(
            taskId = "path",
            input = "现在几点",
            status = TaskStatus.COMPLETED,
            completionPath = CompletionPath.LOCAL_INTENT,
        )
        val restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(original))
        assertEquals(CompletionPath.LOCAL_INTENT, restored.completionPath)
        val localLlm = original.copy(completionPath = CompletionPath.LOCAL_LLM)
        assertEquals(
            CompletionPath.LOCAL_LLM,
            TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(localLlm)).completionPath,
        )
        val remote = original.copy(completionPath = CompletionPath.REMOTE_LLM)
        assertEquals(
            CompletionPath.REMOTE_LLM,
            TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(remote)).completionPath,
        )
    }

    @Test
    fun snapshotRoundTripPreservesTimestamps() {
        val original = AgentTask(
            taskId = "timed",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_000_003_000L,
            priorTurns = listOf(ConversationTurn("查电量", "63%")),
        )
        val restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(original))
        assertEquals(1_700_000_000_000L, restored.startedAt)
        assertEquals(1_700_000_003_000L, restored.endedAt)
        val encoded = TaskSnapshotCodec.encode(original)
        assertTrue(encoded.contains("startedAt"))
        assertTrue(encoded.contains("endedAt"))
        assertTrue(!encoded.contains("priorTurns"))
        assertTrue(restored.priorTurns.isEmpty())
    }

    @Test
    fun snapshotDecodeWithoutTimestampsStaysNull() {
        val restored = TaskSnapshotCodec.decode(
            """{"taskId":"old","input":"查电量","status":"COMPLETED","loopCount":0,"maxLoops":8,"toolTrace":[],"finalAnswer":"好了","lastError":null,"streamingText":null,"retrievedMemories":[],"attachments":[]}""",
        )
        assertNull(restored.startedAt)
        assertNull(restored.endedAt)
        val encoded = TaskSnapshotCodec.encode(AgentTask(taskId = "old", input = "查电量"))
        assertTrue(!encoded.contains("startedAt"))
        assertTrue(!encoded.contains("endedAt"))
    }

    @Test
    fun snapshotRoundTripPreservesSpeakReply() {
        val original = AgentTask(
            taskId = "snap-voice",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
            speakReply = true,
        )
        val restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(original))
        assertEquals(true, restored.speakReply)
    }

    @Test
    fun submitSpeakReplySurvivesCompletedAndRetryCopy() = runTest {
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
        manager.submit("我现在手机还有多少电？", speakReply = true)
        advanceUntilIdle()
        val completed = manager.task.value
        requireNotNull(completed)
        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertEquals(true, completed.speakReply)
        assertEquals(true, store.listRecent(1).single().speakReply)
        manager.seed(
            completed.copy(status = TaskStatus.FAILED, lastError = UserFacingErrors.NETWORK_FAILED),
        )
        manager.submit(completed.input, speakReply = completed.speakReply)
        advanceUntilIdle()
        val retried = manager.task.value
        requireNotNull(retried)
        assertEquals(TaskStatus.COMPLETED, retried.status)
        assertEquals(true, retried.speakReply)
        assertEquals(TaskStatus.COMPLETED, retried.status)
    }

    @Test
    fun snapshotDecodeWithoutConversationIdUsesDefault() {
        val restored = TaskSnapshotCodec.decode(
            """{"taskId":"old","input":"查电量","status":"COMPLETED","loopCount":0,"maxLoops":8,"toolTrace":[],"finalAnswer":"好了","lastError":null,"streamingText":null,"retrievedMemories":[],"attachments":[]}""",
        )
        assertEquals(ConversationIds.DEFAULT, restored.conversationId)
    }

    @Test
    fun snapshotDecodeBlankConversationIdUsesDefault() {
        val restored = TaskSnapshotCodec.decode(
            """{"taskId":"old","input":"查电量","status":"COMPLETED","loopCount":0,"maxLoops":8,"toolTrace":[],"finalAnswer":"好了","lastError":null,"streamingText":null,"retrievedMemories":[],"attachments":[],"conversationId":"  "}""",
        )
        assertEquals(ConversationIds.DEFAULT, restored.conversationId)
    }

    @Test
    fun snapshotRoundTripPreservesConversationId() {
        val original = AgentTask(
            taskId = "c1",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
            conversationId = "thread-a",
        )
        val restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(original))
        assertEquals("thread-a", restored.conversationId)
    }

    @Test
    fun snapshotOmitsPriorTurns() {
        val original = AgentTask(
            taskId = "c1",
            input = "他叫什么",
            status = TaskStatus.THINKING,
            conversationId = "thread-a",
            priorTurns = listOf(
                ConversationTurn("我同事叫张伟", "好的，他叫张伟。"),
            ),
        )
        val encoded = TaskSnapshotCodec.encode(original)
        assertTrue(!encoded.contains("priorTurns"))
        assertTrue(!encoded.contains("我同事叫张伟"))
        assertTrue(!encoded.contains("好的，他叫张伟。"))
        val restored = TaskSnapshotCodec.decode(encoded)
        assertTrue(restored.priorTurns.isEmpty())
    }

    @Test
    fun listByConversationIsOldestFirstAndIgnoresOtherThreads() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(taskId = "a1", input = "一", status = TaskStatus.COMPLETED, conversationId = "a"),
        )
        store.upsert(
            AgentTask(taskId = "b1", input = "旁", status = TaskStatus.COMPLETED, conversationId = "b"),
        )
        store.upsert(
            AgentTask(taskId = "a2", input = "二", status = TaskStatus.COMPLETED, conversationId = "a"),
        )
        val threadA = store.listByConversation("a")
        assertEquals(listOf("a1", "a2"), threadA.map { it.taskId })
        assertEquals(listOf("b1"), store.listByConversation("b").map { it.taskId })
    }
}
