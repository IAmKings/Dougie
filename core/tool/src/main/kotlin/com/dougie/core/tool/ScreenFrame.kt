package com.dougie.core.tool

import com.dougie.core.model.AttachmentLimits

class ScreenFrame(
    val id: String,
    val width: Int,
    val height: Int,
    val gray: ByteArray,
) {
    init {
        require(width > 0 && height > 0)
        require(gray.size == width * height)
    }

    override fun toString(): String = "ScreenFrame(id=$id, width=$width, height=$height)"
}

interface ScreenFrameStore {
    fun put(frame: ScreenFrame): Boolean
    fun get(id: String): ScreenFrame?
    fun last(): ScreenFrame?
    fun remove(id: String)
    fun size(): Int
    fun pin()
    fun pinId(id: String): Boolean
    fun pinned(): ScreenFrame?
    fun clearPin()
    fun clearAll()
}

class InMemoryScreenFrameStore : ScreenFrameStore {
    private val frames = LinkedHashMap<String, ScreenFrame>()
    @Volatile
    private var pinnedId: String? = null

    @Synchronized
    override fun put(frame: ScreenFrame): Boolean {
        val hold = pinnedId
        if (hold != null && hold != frame.id) return false
        if (!frames.containsKey(frame.id) && frames.size >= AttachmentLimits.MAX) return false
        frames[frame.id] = frame
        return true
    }

    @Synchronized
    override fun get(id: String): ScreenFrame? = frames[id]

    @Synchronized
    override fun last(): ScreenFrame? = frames.values.lastOrNull()

    @Synchronized
    override fun remove(id: String) {
        if (pinnedId == id) return
        frames.remove(id)
    }

    @Synchronized
    override fun size(): Int = frames.size

    @Synchronized
    override fun pin() {
        pinnedId = frames.values.lastOrNull()?.id
    }

    @Synchronized
    override fun pinId(id: String): Boolean {
        if (!frames.containsKey(id)) return false
        pinnedId = id
        return true
    }

    @Synchronized
    override fun pinned(): ScreenFrame? {
        val id = pinnedId ?: return null
        return frames[id]
    }

    @Synchronized
    override fun clearPin() {
        pinnedId = null
    }

    @Synchronized
    override fun clearAll() {
        pinnedId = null
        frames.clear()
    }
}
