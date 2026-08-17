package com.dougie.tool.accessibility

import com.dougie.core.model.ToolContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TapSwipeToolTest {
    @Test
    fun executeDoesNotClickAndReturnsNotEnabled() = runBlocking {
        val tool = TapSwipeTool { true }
        val result = tool.execute("{}", ToolContext(taskId = "t", toolCallId = "c"))
        assertEquals(TapSwipeTool.NOT_ENABLED, result.error)
        assertFalse(result.json.contains("clicked"))
        assertEquals(false, result.isFatal)
    }

    @Test
    fun executeWithoutConsentDoesNotTap() = runBlocking {
        val tool = TapSwipeTool { false }
        val result = tool.execute("{}", ToolContext(taskId = "t", toolCallId = "c"))
        assertEquals(TapSwipeTool.CONSENT_REQUIRED, result.error)
        assertFalse(result.json.contains("clicked"))
    }
}
