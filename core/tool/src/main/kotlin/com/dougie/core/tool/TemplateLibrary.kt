package com.dougie.core.tool

object TemplateLibrary {
    const val SOLID = "solid"
    const val LOGO = "logo"

    private const val LOGO_SIZE = 24

    fun ids(): List<String> = listOf(SOLID, LOGO)

    fun frame(templateId: String): ScreenFrame? {
        return when (templateId) {
            SOLID -> ScreenFrame(
                id = SOLID,
                width = 8,
                height = 8,
                gray = ByteArray(64) { -1 },
            )
            LOGO -> ScreenFrame(
                id = LOGO,
                width = LOGO_SIZE,
                height = LOGO_SIZE,
                gray = logoGray(LOGO_SIZE),
            )
            else -> null
        }
    }

    /** High-contrast D mark so NCC is not degenerate (not a uniform block). */
    private fun logoGray(size: Int): ByteArray {
        val gray = ByteArray(size * size)
        val left = size / 6
        val right = size - size / 6
        val top = size / 6
        val bottom = size - size / 6
        val stem = (size / 5).coerceAtLeast(3)
        val outerRx = (right - left).toDouble() / 2.0
        val outerRy = (bottom - top).toDouble() / 2.0
        val cx = left + outerRx
        val cy = (top + bottom) / 2.0
        val innerRx = outerRx * 0.42
        val innerRy = outerRy * 0.48
        for (y in 0 until size) {
            for (x in 0 until size) {
                val inStem = x in left until (left + stem) && y in top until bottom
                val dx = x - cx
                val dy = y - cy
                val inOuter =
                    (dx * dx) / (outerRx * outerRx) + (dy * dy) / (outerRy * outerRy) <= 1.0
                val inInner =
                    (dx * dx) / (innerRx * innerRx) + (dy * dy) / (innerRy * innerRy) <= 1.0
                val inBowl = x >= left + stem - 1 && y in top until bottom && inOuter && !inInner
                if (inStem || inBowl) {
                    gray[y * size + x] = -1
                }
            }
        }
        return gray
    }
}
