package com.dougie.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StandingRulesTest {
    @Test
    fun blankCollapsesToEmpty() {
        assertEquals("", StandingRules.clamp("  \n\t"))
    }

    @Test
    fun clampsByCodePointNotUtf16() {
        val emoji = "\uD840\uDC00"
        val raw = "答".repeat(239) + emoji + "尾"
        val clamped = StandingRules.clamp(raw)
        assertEquals(StandingRules.MAX_CODE_POINTS, clamped.codePointCount(0, clamped.length))
        assertEquals(emoji, clamped.substring(clamped.offsetByCodePoints(0, 239)))
        assertFalse(clamped.contains("尾"))
    }

    @Test
    fun limitKeepsSpacesInsideTheCap() {
        assertEquals("先给结论 再举例", StandingRules.limit("先给结论 再举例"))
    }
}
