package com.dougie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTitlesTest {
    @Test
    fun setTitleStoresTrimmedNameAndBlankRemoves() {
        val titles = InMemoryConversationTitles()
        titles.setTitle("window-a", " 工作\n ")
        assertEquals(mapOf("window-a" to "工作"), titles.titles())
        titles.setTitle("window-a", "   ")
        assertTrue(titles.titles().isEmpty())
    }

    @Test
    fun allowsDuplicateNames() {
        val titles = InMemoryConversationTitles()
        titles.setTitle("a", "工作")
        titles.setTitle("b", "工作")
        assertEquals(mapOf("a" to "工作", "b" to "工作"), titles.titles())
    }
}
