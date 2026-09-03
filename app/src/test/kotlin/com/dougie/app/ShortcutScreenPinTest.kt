package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.model.CompletionPath
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.tool.InMemoryScreenFrameStore
import com.dougie.core.tool.ScreenFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutScreenPinTest {
    private fun localScreenTask() = AgentTask(
        taskId = "t",
        input = "截个屏",
        status = TaskStatus.COMPLETED,
        completionPath = CompletionPath.LOCAL_INTENT,
        toolTrace = listOf(
            ToolTraceEntry(
                toolCallId = "intent-route-1",
                toolName = "screen_capture",
                argsSummary = "{}",
                status = ToolTraceStatus.SUCCESS,
            ),
        ),
    )

    @Test
    fun adoptCopiesJpegIntoEmptyComposer() {
        val store = InMemoryScreenFrameStore()
        val session = ChatAttachmentSession(store)
        val frame = ScreenFrame("cap1", 2, 2, ByteArray(4))
        val jpeg = byteArrayOf(1, 2, 3)
        assertTrue(store.put(frame))
        store.putJpeg(frame.id, jpeg)
        assertTrue(ShortcutScreenPin.adoptIntoComposer(localScreenTask(), session, store))
        assertEquals(listOf("cap1"), session.snapshot().map { it.id })
        assertEquals(jpeg.toList(), session.jpeg("cap1")?.toList())
        assertEquals("cap1", store.get("cap1")?.id)
    }

    @Test
    fun remoteLlmCaptureDoesNotAdopt() {
        val store = InMemoryScreenFrameStore()
        val session = ChatAttachmentSession(store)
        val frame = ScreenFrame("cap1", 2, 2, ByteArray(4))
        assertTrue(store.put(frame))
        val llm = localScreenTask().copy(completionPath = CompletionPath.REMOTE_LLM)
        assertFalse(ShortcutScreenPin.adoptIntoComposer(llm, session, store))
        assertTrue(session.snapshot().isEmpty())
    }
}
