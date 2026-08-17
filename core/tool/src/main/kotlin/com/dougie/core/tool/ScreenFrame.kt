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
}

class InMemoryScreenFrameStore : ScreenFrameStore {
    @Volatile
    private var lastFrame: ScreenFrame? = null

    override fun put(frame: ScreenFrame) {
        lastFrame = frame
    }

    override fun last(): ScreenFrame? = lastFrame
}
