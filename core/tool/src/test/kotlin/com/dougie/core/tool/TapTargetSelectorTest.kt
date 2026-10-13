package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTargetSelectorTest {

    private fun candidate(
        text: String,
        clickable: Boolean = true,
        centerX: Int = 100,
        centerY: Int = 100,
        desc: String = "",
    ) = TapCandidate(text, desc, clickable, centerX, centerY)

    @Test
    fun selectsClickableMatchOverNonClickable() {
        val candidates = listOf(
            candidate("记录", clickable = false, centerY = 300),
            candidate("记录", clickable = true, centerY = 900),
        )
        val target = TapTargetSelector.select(candidates, "记录")
        assertEquals(true, target!!.clickable)
        assertEquals(900, target.centerY)
    }

    @Test
    fun prefersLastDocumentOrderAmongEquals() {
        val candidates = listOf(
            candidate("消息", centerY = 900),
            candidate("记录", centerY = 900),
            candidate("我的", centerY = 900),
        )
        val target = TapTargetSelector.select(candidates, "记")
        assertEquals("记录", target!!.text)
    }

    @Test
    fun matchesContentDescriptionAndIgnoresWhitespace() {
        val candidates = listOf(candidate("", desc = "记 录", centerY = 500))
        val target = TapTargetSelector.select(candidates, "记录")
        assertEquals(500, target!!.centerY)
    }

    @Test
    fun returnsNullWhenNothingMatchesOrNeedleBlank() {
        assertNull(TapTargetSelector.select(listOf(candidate("发送")), "记录"))
        assertNull(TapTargetSelector.select(listOf(candidate("记录")), "  "))
        assertNull(TapTargetSelector.select(emptyList(), "记录"))
    }
}
