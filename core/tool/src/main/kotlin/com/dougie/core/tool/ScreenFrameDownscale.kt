package com.dougie.core.tool

data class MatchPrep(
    val image: ScreenFrame,
    val template: ScreenFrame,
    val scaleX: Double,
    val scaleY: Double,
) {
    fun toOriginalX(x: Int): Int = (x * scaleX).toInt()

    fun toOriginalY(y: Int): Int = (y * scaleY).toInt()
}

object ScreenFrameDownscale {
    const val WORKING_WIDTH = 320

    fun prepare(
        image: ScreenFrame,
        template: ScreenFrame,
        workingWidth: Int = WORKING_WIDTH,
    ): MatchPrep {
        if (image.width <= workingWidth) {
            return MatchPrep(image = image, template = template, scaleX = 1.0, scaleY = 1.0)
        }
        val newW = workingWidth
        val newH = ((image.height.toLong() * newW) / image.width).toInt().coerceAtLeast(1)
        val scaledImage = scaleNearest(image, newW, newH)
        val tw = ((template.width.toLong() * newW) / image.width).toInt().coerceAtLeast(1)
        val th = ((template.height.toLong() * newH) / image.height).toInt().coerceAtLeast(1)
        val scaledTemplate = scaleNearest(template, tw, th)
        return MatchPrep(
            image = scaledImage,
            template = scaledTemplate,
            scaleX = image.width.toDouble() / newW,
            scaleY = image.height.toDouble() / newH,
        )
    }

    fun scaleNearest(source: ScreenFrame, newWidth: Int, newHeight: Int): ScreenFrame {
        require(newWidth > 0 && newHeight > 0)
        val gray = ByteArray(newWidth * newHeight)
        for (y in 0 until newHeight) {
            val srcY = (((y + 0.5) * source.height) / newHeight).toInt().coerceIn(0, source.height - 1)
            for (x in 0 until newWidth) {
                val srcX = (((x + 0.5) * source.width) / newWidth).toInt().coerceIn(0, source.width - 1)
                gray[y * newWidth + x] = source.gray[srcY * source.width + srcX]
            }
        }
        return ScreenFrame(id = source.id, width = newWidth, height = newHeight, gray = gray)
    }
}
