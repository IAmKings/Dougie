package com.dougie.core.tool

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeVerifierTest {

    @Test
    fun matchesNeedleWhenSnapshotContainsIt() = runTest {
        val snapshot = TreeSnapshot(listOf("收件人", "今晚一起吃饭", "发送"))
        assertTrue(TreeVerifier.contains(snapshot, "今晚一起吃饭"))
    }

    @Test
    fun matchesNeedleAcrossWhitespaceDifferences() = runTest {
        val snapshot = TreeSnapshot(listOf("138 0013 8000", "明早八点\n出发"))
        assertTrue(TreeVerifier.contains(snapshot, "13800138000"))
        assertTrue(TreeVerifier.contains(snapshot, "明早八点出发"))
    }

    @Test
    fun doesNotMatchWhenNeedleAbsentOrBlank() {
        val snapshot = TreeSnapshot(listOf("收件人", "发送"))
        assertFalse(TreeVerifier.contains(snapshot, "今晚见"))
        assertFalse(TreeVerifier.contains(snapshot, "   "))
        assertFalse(TreeVerifier.contains(TreeSnapshot(emptyList()), "今晚见"))
    }

    @Test
    fun returnsTrueWhenSnapshotAlreadyContainsNeedle() = runTest {
        val probe = FakeTreeSnapshotPort(current = TreeSnapshot(listOf("今晚见")))
        val verified = TreeVerifier.awaitContains(
            probe,
            "今晚见",
            timeoutMs = 1_000,
            pollIntervalMs = 100,
        )
        assertTrue(verified)
        assertEquals(1, probe.callCount)
    }

    @Test
    fun pollsUntilNeedleAppearsWithinBudget() = runTest {
        val probe = FakeTreeSnapshotPort(current = TreeSnapshot(emptyList()))
        probe.enqueue(
            TreeSnapshot(emptyList()),
            null,
            TreeSnapshot(listOf("草稿", "今晚见")),
        )
        val verified = TreeVerifier.awaitContains(
            probe,
            "今晚见",
            timeoutMs = 5_000,
            pollIntervalMs = 250,
        )
        assertTrue(verified)
        assertEquals(3, probe.callCount)
    }

    @Test
    fun returnsFalseWhenNeedleNeverAppearsWithinBudget() = runTest {
        val probe = FakeTreeSnapshotPort(current = TreeSnapshot(listOf("别的界面")))
        val verified = TreeVerifier.awaitContains(
            probe,
            "今晚见",
            timeoutMs = 1_000,
            pollIntervalMs = 250,
        )
        assertFalse(verified)
        assertEquals(5, probe.callCount)
    }

    @Test
    fun keepsPollingWhileSnapshotUnavailable() = runTest {
        val probe = FakeTreeSnapshotPort(current = null)
        probe.enqueue(null, null, TreeSnapshot(listOf("今晚见")))
        val verified = TreeVerifier.awaitContains(
            probe,
            "今晚见",
            timeoutMs = 5_000,
            pollIntervalMs = 250,
        )
        assertTrue(verified)
        assertEquals(3, probe.callCount)
    }

    @Test
    fun awaitStateChangeDetectsTextSetChangeImmediately() = runTest {
        val before = TreeSnapshot(listOf("页面一"))
        val probe = FakeTreeSnapshotPort(current = TreeSnapshot(listOf("页面二")))
        assertTrue(TreeVerifier.awaitStateChange(before, probe, timeoutMs = 1_000, pollIntervalMs = 100))
        assertEquals(1, probe.callCount)
    }

    @Test
    fun awaitStateChangeDetectsCheckedCountChange() = runTest {
        val before = TreeSnapshot(listOf("设置"), checkedCount = 1, selectedCount = 0)
        val probe = FakeTreeSnapshotPort(current = TreeSnapshot(listOf("设置"), checkedCount = 0, selectedCount = 0))
        assertTrue(TreeVerifier.awaitStateChange(before, probe, timeoutMs = 1_000, pollIntervalMs = 100))
    }

    @Test
    fun awaitStateChangePollsUntilChangeAppears() = runTest {
        val before = TreeSnapshot(listOf("页面一"))
        val probe = FakeTreeSnapshotPort(current = TreeSnapshot(listOf("页面一")))
        probe.enqueue(
            TreeSnapshot(listOf("页面一")),
            TreeSnapshot(listOf("页面一"), checkedCount = 1),
        )
        assertTrue(TreeVerifier.awaitStateChange(before, probe, timeoutMs = 5_000, pollIntervalMs = 250))
        assertEquals(2, probe.callCount)
    }

    @Test
    fun awaitStateChangeReturnsFalseWhenNothingChanges() = runTest {
        val before = TreeSnapshot(listOf("页面一"), checkedCount = 2, selectedCount = 1)
        val probe = FakeTreeSnapshotPort(current = TreeSnapshot(listOf("页面一"), checkedCount = 2, selectedCount = 1))
        assertFalse(
            TreeVerifier.awaitStateChange(before, probe, timeoutMs = 500, pollIntervalMs = 250),
        )
    }

    @Test
    fun awaitStateChangeTreatsNullSnapshotAsNoChangeAndKeepsPolling() = runTest {
        val before = TreeSnapshot(listOf("页面一"))
        val probe = FakeTreeSnapshotPort(current = null)
        probe.enqueue(null, TreeSnapshot(listOf("页面二")))
        assertTrue(TreeVerifier.awaitStateChange(before, probe, timeoutMs = 5_000, pollIntervalMs = 250))
    }
}
