package com.dougie.cli

import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeBatteryLoopTest {
    @Test
    fun fakeLoopCompletesThreeBatteryTools() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = fakeBatteryManager(dispatcher, this, stepDelayMs = 0)
        manager.submit(FAKE_BATTERY_PROMPT)
        advanceUntilIdle()
        val task = manager.task.filterNotNull().first {
            it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED
        }
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(3, task.toolTrace.size)
        assertEquals(3, task.loopCount)
        assertTrue(task.toolTrace.all { it.toolName == "battery" })
        assertTrue(task.toolTrace.all { it.status == ToolTraceStatus.SUCCESS })
        val line = formatSnapshot(task)
        assertTrue(line.contains("status=COMPLETED"))
        assertTrue(line.contains("battery:SUCCESS"))
        assertTrue(line.contains("loop=3"))
        assertTrue(!line.contains("resultJson"))
        assertTrue(!line.contains("battery_percent"))
        assertTrue(!line.contains(FAKE_BATTERY_PROMPT))
        task.toolTrace.forEach { entry ->
            assertTrue(!line.contains(entry.argsSummary) || entry.argsSummary.isEmpty())
        }
    }

    @Test
    fun logOnlyFlagNeedsNoValue() {
        assertTrue(parseLogOnly(arrayOf("--log-only")))
        assertTrue(!parseLogOnly(arrayOf()))
    }
}
