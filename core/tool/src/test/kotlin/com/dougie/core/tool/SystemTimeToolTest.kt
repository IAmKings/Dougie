package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SystemTimeToolTest {

    @Test
    fun returnsIsoLocalZoneAndEpochForFixedClock() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-08-17T01:30:00Z"), ZoneId.of("Asia/Shanghai"))
        val tool = SystemTimeTool(clock)
        val result = tool.execute("{}", ToolContext(taskId = "t", toolCallId = "c1"))
        assertTrue(result.json.contains("\"iso_local\":\"2026-08-17T09:30:00"))
        assertTrue(result.json.contains("\"zone\":\"Asia/Shanghai\""))
        assertTrue(result.json.contains("\"epoch_ms\":${Instant.parse("2026-08-17T01:30:00Z").toEpochMilli()}"))
        assertEquals("time", tool.name)
    }
}
