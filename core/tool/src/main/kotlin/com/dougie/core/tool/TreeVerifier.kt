package com.dougie.core.tool

import kotlinx.coroutines.delay

/**
 * Deterministic verification for content effects: poll the tree snapshot until the needle
 * is visible in the active window or the budget runs out. Matching is whitespace-
 * insensitive (both sides drop all whitespace before the substring check), so grouped
 * number rendering and multi-line bodies still match. Time is accumulated from delays,
 * so tests run on virtual time.
 */
object TreeVerifier {
    const val DEFAULT_TIMEOUT_MS = 5_000L
    const val DEFAULT_POLL_INTERVAL_MS = 250L
    const val DEFAULT_STATE_CHANGE_TIMEOUT_MS = 2_000L

    suspend fun awaitContains(
        port: TreeSnapshotPort,
        needle: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    ): Boolean {
        var waitedMs = 0L
        while (true) {
            val snapshot = port.snapshot()
            if (snapshot != null && contains(snapshot, needle)) return true
            if (waitedMs >= timeoutMs) return false
            delay(pollIntervalMs)
            waitedMs += pollIntervalMs
        }
    }

    fun contains(snapshot: TreeSnapshot, needle: String): Boolean {
        val key = compact(needle)
        if (key.isEmpty()) return false
        return snapshot.texts.any { compact(it).contains(key) }
    }

    /**
     * One-sided tap/swipe effect check for the verification layer: poll until the active
     * window's state fingerprint (text set ∪ checked ∪ selected counts) differs from the
     * pre-action snapshot. "No change" returns false and the caller must treat it as
     * verified=null (honest unknown) — never as a failure, because pixel-only effects
     * (photo paging) are invisible here and a false failure would trigger harmful retries.
     */
    suspend fun awaitStateChange(
        before: TreeSnapshot,
        port: TreeSnapshotPort,
        timeoutMs: Long = DEFAULT_STATE_CHANGE_TIMEOUT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    ): Boolean {
        var waitedMs = 0L
        while (true) {
            val snapshot = port.snapshot()
            if (snapshot != null && changed(before, snapshot)) return true
            if (waitedMs >= timeoutMs) return false
            delay(pollIntervalMs)
            waitedMs += pollIntervalMs
        }
    }

    fun changed(before: TreeSnapshot, after: TreeSnapshot): Boolean =
        before.texts.toSet() != after.texts.toSet() ||
            before.checkedCount != after.checkedCount ||
            before.selectedCount != after.selectedCount

    private fun compact(value: String): String = value.filter { !it.isWhitespace() }
}
