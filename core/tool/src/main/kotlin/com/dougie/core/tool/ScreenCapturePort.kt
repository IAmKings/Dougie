package com.dougie.core.tool

interface ScreenCapturePort {
    fun isAppForeground(): Boolean

    fun hasProjectionConsent(): Boolean

    suspend fun capture(): ScreenFrame
}

class FakeScreenCapturePort(
    var foreground: Boolean = true,
    var hasConsent: Boolean = true,
    var nextFrame: ScreenFrame = whiteSquareOnBlack(),
) : ScreenCapturePort {
    var captureCount: Int = 0
        private set

    override fun isAppForeground(): Boolean = foreground

    override fun hasProjectionConsent(): Boolean = hasConsent

    override suspend fun capture(): ScreenFrame {
        captureCount += 1
        return nextFrame
    }
}

fun whiteSquareOnBlack(
    size: Int = 32,
    square: Int = 8,
    x: Int = 12,
    y: Int = 7,
): ScreenFrame {
    val gray = ByteArray(size * size)
    for (row in y until y + square) {
        for (col in x until x + square) {
            gray[row * size + col] = -1
        }
    }
    return ScreenFrame(id = "synthetic", width = size, height = size, gray = gray)
}
