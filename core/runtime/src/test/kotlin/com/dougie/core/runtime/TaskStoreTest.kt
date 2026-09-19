package com.dougie.core.runtime

import com.dougie.core.llm.FakeLlmProvider
import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.AgentTask
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentMeta
import com.dougie.core.model.CompletionPath
import com.dougie.core.model.ConversationHit
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
                status = TaskStatus.AWAITING_CONFIRMATION,
                loopCount = 1,
                startedAt = 1_000L,
                conversationId = "other-thread",
                confirmDeadlineAt = 1_700_000_060_000L,
            ),
        )
        val recovered = recoverInterrupted(store)
        requireNotNull(recovered)
        assertEquals("live", recovered.taskId)
        assertEquals(TaskStatus.FAILED, recovered.status)
        assertEquals(UserFacingErrors.INTERRUPTED, recovered.lastError)
        assertEquals("other-thread", recovered.conversationId)
        assertEquals(1_000L, recovered.startedAt)
        assertNull(recovered.confirmDeadlineAt)
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
        assertNull(restored.confirmDeadlineAt)
        val encoded = TaskSnapshotCodec.encode(AgentTask(taskId = "old", input = "查电量"))
        assertTrue(!encoded.contains("startedAt"))
        assertTrue(!encoded.contains("endedAt"))
        assertTrue(!encoded.contains("confirmDeadlineAt"))
    }

    @Test
    fun snapshotRoundTripPreservesConfirmDeadlineAt() {
        val original = AgentTask(
            taskId = "awaiting",
            input = "打开微信",
            status = TaskStatus.AWAITING_CONFIRMATION,
            confirmDeadlineAt = 1_700_000_060_000L,
        )
        val restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(original))
        assertEquals(1_700_000_060_000L, restored.confirmDeadlineAt)
        val encoded = TaskSnapshotCodec.encode(original)
        assertTrue(encoded.contains("confirmDeadlineAt"))
    }

    @Test
    fun snapshotDecodeWithoutConfirmDeadlineStaysNull() {
        val restored = TaskSnapshotCodec.decode(
            """{"taskId":"old","input":"查电量","status":"AWAITING_CONFIRMATION","loopCount":0,"maxLoops":8,"toolTrace":[],"finalAnswer":null,"lastError":null,"streamingText":null,"retrievedMemories":[],"attachments":[]}""",
        )
        assertNull(restored.confirmDeadlineAt)
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

    @Test
    fun deleteByConversationRemovesMatchingRowsAndProtectsDefault() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(taskId = "d1", input = "默认", status = TaskStatus.COMPLETED, conversationId = ConversationIds.DEFAULT),
        )
        store.upsert(
            AgentTask(taskId = "b1", input = "旁", status = TaskStatus.COMPLETED, conversationId = "b"),
        )
        repeat(51) { index ->
            store.upsert(
                AgentTask(
                    taskId = "a$index",
                    input = "多",
                    status = TaskStatus.COMPLETED,
                    conversationId = "a",
                ),
            )
        }
        assertEquals(0, store.deleteByConversation(ConversationIds.DEFAULT))
        assertEquals(0, store.deleteByConversation("  "))
        assertEquals(0, store.deleteByConversation(""))
        assertEquals(51, store.deleteByConversation("a"))
        assertTrue(store.listByConversation("a").isEmpty())
        assertEquals(listOf("d1"), store.listByConversation(ConversationIds.DEFAULT).map { it.taskId })
        assertEquals(listOf("b1"), store.listByConversation("b").map { it.taskId })
        assertEquals(0, store.deleteByConversation("a"))
    }

    @Test
    fun snapshotRoundTripPreservesConversationHitsAndOmitsPriorTurns() {
        val hits = listOf(
            ConversationHit(
                taskId = "old-uno",
                conversationId = "window-a",
                sourceLabel = "工作 · UNO 项目关键点",
                user = "UNO 项目关键点是本地优先",
                assistant = "记下了：本地优先。",
            ),
        )
        val original = AgentTask(
            taskId = "now",
            input = "UNO 项目有哪些关键点？",
            status = TaskStatus.COMPLETED,
            finalAnswer = "本地优先。",
            retrievedConversationHits = hits,
            priorTurns = listOf(ConversationTurn("我同事叫张伟", "好的，他叫张伟。")),
        )
        val encoded = TaskSnapshotCodec.encode(original)
        val restored = TaskSnapshotCodec.decode(encoded)
        assertEquals(hits, restored.retrievedConversationHits)
        assertTrue(restored.priorTurns.isEmpty())
        assertTrue(encoded.contains("retrievedConversationHits"))
        assertTrue(!encoded.contains("priorTurns"))
        assertTrue(!encoded.contains("我同事叫张伟"))
    }

    @Test
    fun snapshotDecodeWithoutConversationHitsIsEmpty() {
        val restored = TaskSnapshotCodec.decode(
            """{"taskId":"old","input":"查电量","status":"COMPLETED","loopCount":0,"maxLoops":8,"toolTrace":[],"finalAnswer":"好了","lastError":null,"streamingText":null,"retrievedMemories":[],"attachments":[]}""",
        )
        assertTrue(restored.retrievedConversationHits.isEmpty())
    }

    @Test
    fun snapshotDecodeIgnoresUnknownConversationHitKeys() {
        val restored = TaskSnapshotCodec.decode(
            """{"taskId":"now","input":"q","status":"COMPLETED","loopCount":0,"maxLoops":8,"toolTrace":[],"finalAnswer":"a","lastError":null,"streamingText":null,"retrievedMemories":[],"retrievedConversationHits":[{"taskId":"old","conversationId":"window-a","sourceLabel":"工作 · UNO","user":"u","assistant":"s","extra":"ignore-me"}],"attachments":[],"unexpected":true}""",
        )
        assertEquals(1, restored.retrievedConversationHits.size)
        assertEquals("old", restored.retrievedConversationHits.single().taskId)
        assertEquals("工作 · UNO", restored.retrievedConversationHits.single().sourceLabel)
        assertEquals("u", restored.retrievedConversationHits.single().user)
        assertEquals("s", restored.retrievedConversationHits.single().assistant)
    }

    @Test
    fun searchCompletedTurnsReturnsEmptyForBlankQuery() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "uno",
                input = "UNO 项目关键点是本地优先",
                status = TaskStatus.COMPLETED,
                finalAnswer = "记下了：本地优先。",
            ),
        )
        assertTrue(store.searchCompletedTurns("").isEmpty())
        assertTrue(store.searchCompletedTurns("   ").isEmpty())
    }

    @Test
    fun searchCompletedTurnsMatchesInputAndAnswerNewestFirstAndExcludes() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "older",
                input = "UNO 项目关键点是本地优先",
                status = TaskStatus.COMPLETED,
                finalAnswer = "记下了：本地优先。",
                conversationId = "a",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "failed",
                input = "UNO 项目失败轮",
                status = TaskStatus.FAILED,
                finalAnswer = "UNO 项目失败了",
                conversationId = "a",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "blank",
                input = "UNO 项目空白终答",
                status = TaskStatus.COMPLETED,
                finalAnswer = "   ",
                conversationId = "a",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "excluded",
                input = "UNO 项目当前窗口已见",
                status = TaskStatus.COMPLETED,
                finalAnswer = "这轮已在近期对话。",
                conversationId = "b",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "newer",
                input = "补充一下",
                status = TaskStatus.COMPLETED,
                finalAnswer = "UNO 项目还要离线模型。",
                conversationId = "c",
            ),
        )
        val hits = store.searchCompletedTurns(
            query = "UNO 项目关键点",
            excludeTaskIds = setOf("excluded"),
        )
        assertEquals(listOf("newer", "older"), hits.map { it.taskId })
    }

    @Test
    fun searchCompletedTurnsDoesNotMatchToolTrace() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "tools-only",
                input = "无关问题",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好的。",
                toolTrace = listOf(
                    ToolTraceEntry(
                        toolCallId = "c1",
                        toolName = "clipboard_write",
                        argsSummary = """{"text":"UNO 项目关键点"}""",
                        resultJson = """{"ok":"UNO 项目关键点"}""",
                        status = ToolTraceStatus.SUCCESS,
                    ),
                ),
            ),
        )
        assertTrue(store.searchCompletedTurns("UNO 项目关键点").isEmpty())
    }

    @Test
    fun searchCompletedTurnsDoesNotCiteUnrelatedChitChatForUnoQuery() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "liu",
                input = "他是刘备",
                status = TaskStatus.COMPLETED,
                finalAnswer = "记下了，他是刘备。",
                conversationId = "chat-a",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "coffee",
                input = "我平时喝什么咖啡",
                status = TaskStatus.COMPLETED,
                finalAnswer = "你平时喝美式。这个习惯我记住了。",
                conversationId = "chat-b",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "uno",
                input = "UNO 怎么出加2",
                status = TaskStatus.COMPLETED,
                finalAnswer = "UNO 加2要下家摸两张。",
                conversationId = "chat-c",
            ),
        )
        val hits = store.searchCompletedTurns("uno这个玩法")
        assertEquals(listOf("uno"), hits.map { it.taskId })
        assertEquals(listOf("uno", "这个玩法"), conversationSearchNeedles("uno这个玩法"))
        assertTrue(store.searchCompletedTurns("这个").isEmpty())
    }

    @Test
    fun searchHistoryFindsFailedAndCompletedAndSkipsInProgress() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "done",
                input = "UNO 项目关键点",
                status = TaskStatus.COMPLETED,
                finalAnswer = "记下了：本地优先。",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "failed",
                input = "UNO 再试一次",
                status = TaskStatus.FAILED,
                lastError = UserFacingErrors.NETWORK_FAILED,
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "live",
                input = "UNO 正在跑",
                status = TaskStatus.THINKING,
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "error-only",
                input = "无关问题",
                status = TaskStatus.FAILED,
                lastError = "网络失败，请稍后重试。",
            ),
        )
        assertEquals(listOf("failed", "done"), store.searchHistory("UNO").map { it.taskId })
        assertEquals(listOf("error-only"), store.searchHistory("网络失败").map { it.taskId })
        assertEquals(listOf("failed"), store.searchHistory("UNO", limit = 1).map { it.taskId })
    }

    @Test
    fun searchHistoryReturnsEmptyForBlankOrStopwordNeedles() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "uno",
                input = "UNO 项目关键点是本地优先",
                status = TaskStatus.COMPLETED,
                finalAnswer = "记下了：本地优先。",
            ),
        )
        assertTrue(store.searchHistory("").isEmpty())
        assertTrue(store.searchHistory("   ").isEmpty())
        assertTrue(store.searchHistory("这个").isEmpty())
    }

    @Test
    fun searchHistoryDoesNotMatchToolTrace() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "tools-only",
                input = "无关问题",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好的。",
                toolTrace = listOf(
                    ToolTraceEntry(
                        toolCallId = "c1",
                        toolName = "clipboard_write",
                        argsSummary = """{"text":"UNO 项目关键点"}""",
                        resultJson = """{"ok":"UNO 项目关键点"}""",
                        status = ToolTraceStatus.SUCCESS,
                    ),
                ),
            ),
        )
        assertTrue(store.searchHistory("UNO 项目关键点").isEmpty())
    }

    @Test
    fun searchHistoryDoesNotCiteUnrelatedChitChatForUnoQuery() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "liu",
                input = "他是刘备",
                status = TaskStatus.COMPLETED,
                finalAnswer = "记下了，他是刘备。",
                conversationId = "chat-a",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "coffee",
                input = "我平时喝什么咖啡",
                status = TaskStatus.COMPLETED,
                finalAnswer = "你平时喝美式。这个习惯我记住了。",
                conversationId = "chat-b",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "uno",
                input = "UNO 怎么出加2",
                status = TaskStatus.FAILED,
                lastError = UserFacingErrors.NETWORK_FAILED,
                conversationId = "chat-c",
            ),
        )
        assertEquals(listOf("uno"), store.searchHistory("uno这个玩法").map { it.taskId })
        assertTrue(store.searchHistory("这个").isEmpty())
    }

    @Test
    fun searchHistoryFindsTerminalTurnsBeyondRecentFifty() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(
                taskId = "old",
                input = "UNO 怎么出加2",
                status = TaskStatus.FAILED,
                lastError = UserFacingErrors.NETWORK_FAILED,
            ),
        )
        repeat(50) { index ->
            store.upsert(
                AgentTask(
                    taskId = "recent-$index",
                    input = "无关问题 $index",
                    status = TaskStatus.COMPLETED,
                    finalAnswer = "好了",
                ),
            )
        }
        assertEquals(50, store.listRecent(50).size)
        assertTrue(store.listRecent(50).none { it.taskId == "old" })
        assertEquals(listOf("old"), store.searchHistory("UNO").map { it.taskId })
    }

    @Test
    fun deleteByTaskIdRemovesPrimaryKeyAndIgnoresBlank() = runTest {
        val store = InMemoryTaskStore()
        store.upsert(
            AgentTask(taskId = "keep", input = "保留", status = TaskStatus.COMPLETED, finalAnswer = "好了"),
        )
        store.upsert(
            AgentTask(taskId = "drop", input = "丢掉", status = TaskStatus.FAILED, lastError = "网络失败"),
        )
        assertEquals(0, store.deleteByTaskId(""))
        assertEquals(0, store.deleteByTaskId("   "))
        assertEquals(0, store.deleteByTaskId("missing"))
        assertEquals(1, store.deleteByTaskId("drop"))
        assertEquals(listOf("keep"), store.listRecent(10).map { it.taskId })
        assertEquals(0, store.deleteByTaskId("drop"))
        assertEquals(listOf("keep"), store.listByConversation(ConversationIds.DEFAULT).map { it.taskId })
    }
}
