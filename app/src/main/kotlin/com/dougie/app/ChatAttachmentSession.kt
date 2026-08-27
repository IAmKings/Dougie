package com.dougie.app

import com.dougie.core.model.AgentException
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentLimits
import com.dougie.core.model.AttachmentMeta
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ScreenFrame
import com.dougie.core.tool.ScreenFrameStore
import java.util.UUID

class ChatAttachmentSession(
    private val screens: ScreenFrameStore,
) {
    private val order = mutableListOf<AttachmentMeta>()
    private val jpegs = LinkedHashMap<String, ByteArray>()

    @Synchronized
    fun snapshot(): List<AttachmentMeta> = order.toList()

    @Synchronized
    fun remaining(): Int = (AttachmentLimits.MAX - order.size).coerceAtLeast(0)

    @Synchronized
    fun jpeg(id: String): ByteArray? = jpegs[id]

    @Synchronized
    fun addScreen(frame: ScreenFrame): Result<AttachmentMeta> {
        if (order.size >= AttachmentLimits.MAX) {
            return Result.failure(AgentException(UserFacingErrors.ATTACHMENTS_FULL))
        }
        screens.clearPin()
        if (!screens.put(frame)) {
            return Result.failure(AgentException(UserFacingErrors.ATTACHMENTS_FULL))
        }
        val meta = AttachmentMeta(frame.id, AttachmentKind.SCREEN, frame.width, frame.height)
        order.add(meta)
        return Result.success(meta)
    }

    @Synchronized
    fun addPhoto(kind: AttachmentKind, jpeg: ByteArray, width: Int, height: Int): Result<AttachmentMeta> {
        require(kind == AttachmentKind.GALLERY || kind == AttachmentKind.CAMERA)
        if (order.size >= AttachmentLimits.MAX) {
            return Result.failure(AgentException(UserFacingErrors.ATTACHMENTS_FULL))
        }
        val id = UUID.randomUUID().toString()
        val meta = AttachmentMeta(id, kind, width, height)
        jpegs[id] = jpeg
        order.add(meta)
        return Result.success(meta)
    }

    @Synchronized
    fun remove(id: String) {
        val meta = order.find { it.id == id } ?: return
        order.removeAll { it.id == id }
        if (meta.kind == AttachmentKind.SCREEN) {
            screens.remove(id)
        } else {
            jpegs.remove(id)
        }
    }

    @Synchronized
    fun clearComposer() {
        order.clear()
    }

    @Synchronized
    fun releaseAfterTask() {
        order.clear()
        jpegs.clear()
        screens.clearAll()
    }
}
