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
        assertEquals("com.dougie.app.extra.APPLY_PINNED_SCREEN", ChatLaunch.EXTRA_APPLY_PINNED_SCREEN)
        assertFalse(ChatLaunch.EXTRA_APPLY_PINNED_SCREEN.contains("key", ignoreCase = true))
        assertFalse(ChatLaunch.EXTRA_APPLY_PINNED_SCREEN.contains("prompt", ignoreCase = true))
        assertFalse(ChatLaunch.EXTRA_APPLY_PINNED_SCREEN.contains("base64", ignoreCase = true))
        assertFalse(ChatLaunch.applyPinnedScreen(null))
        assertEquals("com.dougie.app.extra.OPEN_PERMISSIONS", ChatLaunch.EXTRA_OPEN_PERMISSIONS)
        assertFalse(ChatLaunch.EXTRA_OPEN_PERMISSIONS.contains("key", ignoreCase = true))
        assertFalse(ChatLaunch.EXTRA_OPEN_PERMISSIONS.contains("prompt", ignoreCase = true))
        assertFalse(ChatLaunch.openPermissions(null))
    }
}
