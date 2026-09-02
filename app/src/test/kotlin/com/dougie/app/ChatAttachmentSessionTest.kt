package com.dougie.app

import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.InMemoryScreenFrameStore
import com.dougie.core.tool.ScreenFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentSessionTest {
    @Test
    fun fifthAddFailsWithChineseFullMessage() {
        val store = InMemoryScreenFrameStore()
        val session = ChatAttachmentSession(store)
        repeat(4) { i ->
            val added = session.addPhoto(AttachmentKind.GALLERY, byteArrayOf(i.toByte()), 8, 8)
            assertTrue(added.isSuccess)
        }
        val fifth = session.addPhoto(AttachmentKind.CAMERA, byteArrayOf(9), 8, 8)
        assertTrue(fifth.isFailure)
        assertEquals(
            UserFacingErrors.ATTACHMENTS_FULL,
            (fifth.exceptionOrNull() as com.dougie.core.model.AgentException).userMessage,
        )
        assertEquals(4, session.snapshot().size)
    }

    @Test
    fun addScreenPinsIntoStoreAndRemoveDropsFrame() {
        val store = InMemoryScreenFrameStore()
        val session = ChatAttachmentSession(store)
        val frame = ScreenFrame("cap1", 2, 2, ByteArray(4))
        assertTrue(session.addScreen(frame).isSuccess)
        assertEquals("cap1", store.get("cap1")?.id)
        session.remove("cap1")
        assertEquals(null, store.get("cap1"))
        assertEquals(null, session.jpeg("cap1"))
        assertTrue(session.snapshot().isEmpty())
    }

    @Test
    fun addScreenKeepsPreviewJpegUntilRemove() {
        val store = InMemoryScreenFrameStore()
        val session = ChatAttachmentSession(store)
        val frame = ScreenFrame("cap1", 2, 2, ByteArray(4))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        assertTrue(session.addScreen(frame, jpeg).isSuccess)
        assertEquals(jpeg.toList(), session.jpeg("cap1")?.toList())
        session.remove("cap1")
        assertEquals(null, session.jpeg("cap1"))
    }

    @Test
    fun twoScreenshotsBothStayInComposer() {
        val store = InMemoryScreenFrameStore()
        val session = ChatAttachmentSession(store)
        assertTrue(session.addScreen(ScreenFrame("a", 2, 2, ByteArray(4)), byteArrayOf(1)).isSuccess)
        assertTrue(session.addScreen(ScreenFrame("b", 2, 2, ByteArray(4)), byteArrayOf(2)).isSuccess)
        assertEquals(listOf("a", "b"), session.snapshot().map { it.id })
        assertEquals(byteArrayOf(1).toList(), session.jpeg("a")?.toList())
        assertEquals(byteArrayOf(2).toList(), session.jpeg("b")?.toList())
    }
}
