package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSpeakTextTest {
    @Test
    fun dateAndTimeDigitsBecomeChinese() {
        assertEquals(
            "现在是二零二六年八月二十九日十五点零三分",
            TtsSpeakText.forOffline("现在是2026年8月29日15点03分"),
        )
        assertEquals("十五点零三", TtsSpeakText.forOffline("15:03"))
        assertEquals("零三", TtsSpeakText.forOffline("03"))
        assertEquals("你好", TtsSpeakText.forOffline("你好"))
        assertEquals("十", TtsSpeakText.forOffline("10"))
        assertEquals("二十", TtsSpeakText.forOffline("20"))
    }
}
