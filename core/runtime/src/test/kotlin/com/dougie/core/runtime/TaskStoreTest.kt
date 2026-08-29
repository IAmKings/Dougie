package com.dougie.core.runtime

import com.dougie.core.llm.FakeLlmProvider
import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.AgentTask
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentMeta
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.FakeBatteryTool
import com.dougie.core.tool.InMemoryScreenFrameStore
import com.dougie.core.tool.whiteSquareOnBlack
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
    }

    @Test
    fun snapshotDecodeWithoutSpeakReplyDefaultsFalse() {
        val restored = TaskSnapshotCodec.decode(
            """{"taskId":"old","input":"查电量","status":"COMPLETED","loopCount":0,"maxLoops":8,"toolTrace":[],"finalAnswer":"好了","lastError":null,"streamingText":null,"retrievedMemories":[],"attachments":[]}""",
        )
        assertEquals(false, restored.speakReply)
        assertEquals("好了", restored.finalAnswer)
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
}
