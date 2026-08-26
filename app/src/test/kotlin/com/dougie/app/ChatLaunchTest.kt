package com.dougie.app

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatLaunchTest {
    @Test
    fun extraAndFlagsAreStableAndNotSecrets() {
        assertEquals("com.dougie.app.extra.OPEN_CHAT", ChatLaunch.EXTRA_OPEN_CHAT)
        assertFalse(ChatLaunch.EXTRA_OPEN_CHAT.contains("key", ignoreCase = true))
        assertFalse(ChatLaunch.EXTRA_OPEN_CHAT.contains("prompt", ignoreCase = true))
        val expected =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(expected, ChatLaunch.activityFlags)
        assertFalse(ChatLaunch.requestsChat(null))
        assertEquals("com.dougie.app.extra.SCHEDULE_ID", ChatLaunch.EXTRA_SCHEDULE_ID)
        assertFalse(ChatLaunch.EXTRA_SCHEDULE_ID.contains("key", ignoreCase = true))
        assertFalse(ChatLaunch.EXTRA_SCHEDULE_ID.contains("prompt", ignoreCase = true))
        assertEquals(null, ChatLaunch.scheduleId(null))
    }
}
