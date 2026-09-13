package com.dougie.core.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

internal const val DEFAULT_MIN_COSINE = 0.45f

fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
    if (left.size != right.size || left.isEmpty()) return 0f
    var dot = 0f
    var leftNorm = 0f
    var rightNorm = 0f
    for (i in left.indices) {
        val a = left[i]
        val b = right[i]
        dot += a * b
        leftNorm += a * a
        rightNorm += b * b
    }
    val denom = sqrt(leftNorm.toDouble()) * sqrt(rightNorm.toDouble())
    if (denom < 1e-8) return 0f
    return (dot / denom).toFloat()
}

fun l2Normalize(values: FloatArray): FloatArray {
    var sum = 0f
    for (v in values) sum += v * v
    val norm = sqrt(sum.toDouble()).toFloat()
    if (norm < 1e-8f) return values
    return FloatArray(values.size) { i -> values[i] / norm }
}

/** CLS (first hidden) when rank-3 `[1,seq,H]`; already-pooled `[1,H]` stays as-is. Then L2. */
fun poolClsOrVector(raw: FloatArray, hiddenSize: Int = 512): FloatArray {
    if (raw.isEmpty() || hiddenSize <= 0) return raw
    val pooled = when {
        raw.size == hiddenSize -> raw
        raw.size >= hiddenSize && raw.size % hiddenSize == 0 -> raw.copyOfRange(0, hiddenSize)
        else -> raw
    }
    return l2Normalize(pooled)
}

fun floatsToLittleEndian(values: FloatArray): ByteArray {
    val buf = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (v in values) buf.putFloat(v)
    return buf.array()
}

fun littleEndianToFloats(bytes: ByteArray?): FloatArray? {
    if (bytes == null || bytes.size < 4 || bytes.size % 4 != 0) return null
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(bytes.size / 4) { buf.getFloat() }
}
