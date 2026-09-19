package com.dougie.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryTransitionTest {
    @Test
    fun cardTapMatchingTaskIdUsesSharedBounds() {
        assertTrue(usesChatHistorySharedBounds("t1", sharedTaskId = "t1", reduceMotion = false))
        assertEquals(300, CHAT_HISTORY_TRANSITION_MS)
    }

    @Test
    fun animatorDurationScaleZeroSkipsSharedBounds() {
        assertFalse(usesChatHistorySharedBounds("t1", sharedTaskId = "t1", reduceMotion = true))
    }

    @Test
    fun bottomNavAndBackDoNotSetSharedBounds() {
        assertFalse(usesChatHistorySharedBounds("t1", sharedTaskId = null, reduceMotion = false))
        assertFalse(usesChatHistorySharedBounds("t1", sharedTaskId = "other", reduceMotion = false))
    }
}
