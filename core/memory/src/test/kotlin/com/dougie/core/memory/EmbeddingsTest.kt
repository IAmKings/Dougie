package com.dougie.core.memory

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingsTest {
    @Test
    fun littleEndianRoundTrip() {
        val values = floatArrayOf(0.45f, -1f, 0f)
        val bytes = floatsToLittleEndian(values)
        assertEquals(12, bytes.size)
        assertArrayEquals(values, littleEndianToFloats(bytes)!!, 0f)
    }

    @Test
    fun nullOrTruncatedBlobIsIgnored() {
        assertNull(littleEndianToFloats(null))
        assertNull(littleEndianToFloats(ByteArray(3)))
        assertNull(littleEndianToFloats(ByteArray(6)))
    }

    @Test
    fun cosineIsOneForIdenticalVectors() {
        val vec = floatArrayOf(0.3f, 0.4f)
        assertEquals(1f, cosineSimilarity(vec, vec), 1e-6f)
        assertEquals(0f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        assertTrue(cosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 0f)) == 0f)
    }
}
