package com.dougie.core.tool

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
    fun put(frame: ScreenFrame)
    fun last(): ScreenFrame?
    fun pin()
    fun pinned(): ScreenFrame?
    fun clearPin()
}

class InMemoryScreenFrameStore : ScreenFrameStore {
    @Volatile
    private var lastFrame: ScreenFrame? = null
    @Volatile
    private var pinnedId: String? = null

    override fun put(frame: ScreenFrame) {
        val hold = pinnedId
        if (hold != null && hold != frame.id) return
        lastFrame = frame
    }

    override fun last(): ScreenFrame? = lastFrame

    override fun pin() {
        pinnedId = lastFrame?.id
    }

    override fun pinned(): ScreenFrame? {
        val id = pinnedId ?: return null
        val frame = lastFrame
        return if (frame?.id == id) frame else null
    }

    override fun clearPin() {
        pinnedId = null
    }
}
