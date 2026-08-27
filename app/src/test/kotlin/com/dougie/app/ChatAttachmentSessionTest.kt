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
        assertTrue(session.snapshot().isEmpty())
    }
}
