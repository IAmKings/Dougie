package com.dougie.core.tool

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundVerifierTest {

    @Test
    fun returnsTrueWhenProbeAlreadyShowsExpectedPackage() = runTest {
        val probe = FakeForegroundProbe(current = "com.example.app")
        val verified = ForegroundVerifier.awaitForeground(
            "com.example.app",
            probe,
            timeoutMs = 1_000,
            pollIntervalMs = 100,
        )
        assertTrue(verified)
        assertEquals(1, probe.callCount)
    }

    @Test
    fun pollsUntilPackageAppearsWithinBudget() = runTest {
        val probe = FakeForegroundProbe(current = null)
        probe.enqueue(null, null, "com.example.app")
        val verified = ForegroundVerifier.awaitForeground(
            "com.example.app",
            probe,
            timeoutMs = 5_000,
            pollIntervalMs = 250,
        )
        assertTrue(verified)
        assertEquals(3, probe.callCount)
    }

    @Test
    fun returnsFalseWhenPackageNeverAppearsWithinBudget() = runTest {
        val probe = FakeForegroundProbe(current = null)
        val verified = ForegroundVerifier.awaitForeground(
            "com.example.app",
            probe,
            timeoutMs = 1_000,
            pollIntervalMs = 250,
        )
        assertFalse(verified)
        assertEquals(5, probe.callCount)
    }

    @Test
    fun trimsProbeValueBeforeComparing() = runTest {
        val probe = FakeForegroundProbe(current = " com.example.app ")
        val verified = ForegroundVerifier.awaitForeground(
            "com.example.app",
            probe,
            timeoutMs = 0,
            pollIntervalMs = 100,
        )
        assertTrue(verified)
    }

    @Test
    fun awaitNotForegroundReturnsTrueWhenForeignPackageImmediate() = runTest {
        val probe = FakeForegroundProbe(current = "com.example.app")
        val returned = ForegroundVerifier.awaitNotForeground(
            "com.dougie.app.sideload",
            probe,
            timeoutMs = 1_000,
            pollIntervalMs = 100,
        )
        assertTrue(returned)
        assertEquals(1, probe.callCount)
    }

    @Test
    fun awaitNotForegroundKeepsPollingWhileSelfInFront() = runTest {
        val probe = FakeForegroundProbe(current = "com.dougie.app.sideload")
        probe.enqueue("com.dougie.app.sideload", "com.dougie.app.sideload", "com.example.app")
        val returned = ForegroundVerifier.awaitNotForeground(
            "com.dougie.app.sideload",
            probe,
            timeoutMs = 5_000,
            pollIntervalMs = 250,
        )
        assertTrue(returned)
        assertEquals(3, probe.callCount)
    }

    @Test
    fun awaitNotForegroundFalseWhenSelfPersistsWithinBudget() = runTest {
        val probe = FakeForegroundProbe(current = "com.dougie.app.sideload")
        val returned = ForegroundVerifier.awaitNotForeground(
            "com.dougie.app.sideload",
            probe,
            timeoutMs = 1_000,
            pollIntervalMs = 250,
        )
        assertFalse(returned)
        assertEquals(5, probe.callCount)
    }

    @Test
    fun awaitNotForegroundTreatsEmptyReadingsAsTransitionsNotForeign() = runTest {
        val probe = FakeForegroundProbe(current = "com.dougie.app.sideload")
        probe.enqueue("com.dougie.app.sideload", null, "", "com.example.app")
        val returned = ForegroundVerifier.awaitNotForeground(
            "com.dougie.app.sideload",
            probe,
            timeoutMs = 5_000,
            pollIntervalMs = 250,
        )
        // 空读数是切换过渡：继续等，直到出现真实的前台包。
        assertTrue(returned)
        assertEquals(4, probe.callCount)
    }
}
