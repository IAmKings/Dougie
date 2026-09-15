package com.dougie.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationDisplayTest {
    @Test
    fun customNonBlankWins() {
        assertEquals(
            "工作",
            conversationDisplayName("window-extra", " 工作 ", "对话 2"),
        )
    }

    @Test
    fun blankCustomFallsBack() {
        assertEquals(
            "对话 2",
            conversationDisplayName("window-extra", "  \n ", "对话 2"),
        )
        assertEquals(
            "默认会话",
            conversationDisplayName(ConversationIds.DEFAULT, "   ", "对话 9"),
        )
        assertEquals(
            "新对话",
            conversationDisplayName("new-uuid", null, "对话 2", isUnlistedNew = true),
        )
    }

    @Test
    fun defaultIgnoresNumberedFallback() {
        assertEquals(
            "默认会话",
            conversationDisplayName(ConversationIds.DEFAULT, null, "对话 2"),
        )
        assertEquals(
            "家里",
            conversationDisplayName(ConversationIds.DEFAULT, "家里", "对话 2"),
        )
    }

    @Test
    fun unlistedNewOnlyWhenNotDefaultAndNoCustom() {
        assertEquals(
            "新对话",
            conversationDisplayName("new-uuid", null, "对话 2", isUnlistedNew = true),
        )
        assertEquals(
            "默认会话",
            conversationDisplayName(ConversationIds.DEFAULT, null, "对话 2", isUnlistedNew = true),
        )
        assertEquals(
            "工作",
            conversationDisplayName("new-uuid", "工作", "对话 2", isUnlistedNew = true),
        )
    }

    @Test
    fun normalizeTrimsNewlinesAndCapsAtTwenty() {
        assertEquals("工作 日程", normalizeConversationTitle("\n工作\n日程\n"))
        assertNull(normalizeConversationTitle("   \n\r  "))
        val long = "一二三四五六七八九十一二三四五六七八九十超额"
        assertEquals("一二三四五六七八九十一二三四五六七八九十", normalizeConversationTitle(long))
        assertEquals(20, normalizeConversationTitle(long)!!.length)
    }
}
