package com.dougie.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.dougie.core.tool.ScreenFrame
import java.io.ByteArrayOutputStream

object ChatImageCodec {
    const val MAX_EDGE = 1280

    fun jpegFromGalleryBytes(raw: ByteArray): Triple<ByteArray, Int, Int>? {
        val full = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        return jpegFromBitmap(full)
    }

    fun jpegFromBitmap(src: Bitmap): Triple<ByteArray, Int, Int> {
        val scaled = scale(src)
        val width = scaled.width
        val height = scaled.height
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
        if (scaled !== src) scaled.recycle()
        return Triple(out.toByteArray(), width, height)
    }

    fun grayPreview(frame: ScreenFrame): Bitmap {
        val bmp = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        var i = 0
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val g = frame.gray[i].toInt() and 0xFF
                bmp.setPixel(x, y, Color.rgb(g, g, g))
                i++
            }
        }
        return bmp
    }

    fun jpegPreview(jpeg: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    private fun scale(src: Bitmap): Bitmap {
        val edge = maxOf(src.width, src.height)
        if (edge <= MAX_EDGE) return src
        val ratio = MAX_EDGE.toFloat() / edge
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
