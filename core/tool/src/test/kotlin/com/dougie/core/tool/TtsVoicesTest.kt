package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsVoicesTest {
    @Test
    fun clampUnknownToDefault() {
        assertEquals(0, TtsVoices.clamp(0))
        assertEquals(14, TtsVoices.clamp(14))
        assertEquals(100, TtsVoices.clamp(100))
        assertEquals(0, TtsVoices.clamp(3))
        assertEquals(0, TtsVoices.clamp(-1))
        assertEquals("默认", TtsVoices.label(99))
        assertEquals("音色一", TtsVoices.label(14))
    }
}
