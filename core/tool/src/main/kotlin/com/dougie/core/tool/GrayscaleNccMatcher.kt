package com.dougie.core.tool

import kotlin.math.abs
import kotlin.math.sqrt

data class NccMatch(
    val x: Int,
    val y: Int,
    val confidence: Double,
)

object GrayscaleNccMatcher {
    const val THRESHOLD = 0.6

    fun match(image: ScreenFrame, template: ScreenFrame): NccMatch? {
        if (template.width > image.width || template.height > image.height) return null
        val maxX = image.width - template.width
        val maxY = image.height - template.height
        var bestX = 0
        var bestY = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (y in 0..maxY) {
            for (x in 0..maxX) {
                val score = nccAt(image, template, x, y)
                if (score > bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
            }
        }
        if (bestScore.isNaN()) return null
        return NccMatch(x = bestX, y = bestY, confidence = bestScore)
    }

    private fun nccAt(image: ScreenFrame, template: ScreenFrame, originX: Int, originY: Int): Double {
        val tw = template.width
        val th = template.height
        val n = tw * th
        var sumI = 0.0
        var sumT = 0.0
        for (ty in 0 until th) {
            val imageRow = (originY + ty) * image.width + originX
            val templateRow = ty * tw
            for (tx in 0 until tw) {
                sumI += image.gray[imageRow + tx].toInt() and 0xFF
                sumT += template.gray[templateRow + tx].toInt() and 0xFF
            }
        }
        val meanI = sumI / n
        val meanT = sumT / n
        var num = 0.0
        var denI = 0.0
        var denT = 0.0
        for (ty in 0 until th) {
            val imageRow = (originY + ty) * image.width + originX
            val templateRow = ty * tw
            for (tx in 0 until tw) {
                val di = (image.gray[imageRow + tx].toInt() and 0xFF) - meanI
                val dt = (template.gray[templateRow + tx].toInt() and 0xFF) - meanT
                num += di * dt
                denI += di * di
                denT += dt * dt
            }
        }
        val den = sqrt(denI * denT)
        if (den < 1e-6) {
            return 1.0 - abs(meanI - meanT) / 255.0
        }
        return num / den
    }
}
