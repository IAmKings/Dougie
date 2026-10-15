package com.dougie.core.tool

import kotlinx.coroutines.delay

/**
 * Deterministic verification for foreground effects: poll the probe until the expected
 * package is in front or the budget runs out. Time is accumulated from delays, so tests
 * run on virtual time and production never checks a wall clock.
 */
object ForegroundVerifier {
    const val DEFAULT_TIMEOUT_MS = 5_000L
    const val DEFAULT_POLL_INTERVAL_MS = 250L

    // Grace window for a tap confirmed from Dougie's own UI: the user may walk back to
    // the target app instead of the chain dying on the self-foreground guard.
    const val SELF_FOREGROUND_GRACE_MS = 6_000L

    suspend fun awaitForeground(
        expectedPackage: String,
        probe: ForegroundProbePort,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    ): Boolean {
        var waitedMs = 0L
        while (true) {
            if (probe.currentForegroundPackage()?.trim() == expectedPackage) return true
            if (waitedMs >= timeoutMs) return false
            delay(pollIntervalMs)
            waitedMs += pollIntervalMs
        }
    }

    /**
     * Inverse of [awaitForeground]: resolve as soon as a non-empty foreign package is in
     * front; transitions through an empty reading keep polling, only [blockedPackage]
     * itself holds until the budget runs out. Time accumulates from delays, so tests run
     * on virtual time.
     */
    suspend fun awaitNotForeground(
        blockedPackage: String,
        probe: ForegroundProbePort,
        timeoutMs: Long = SELF_FOREGROUND_GRACE_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    ): Boolean {
        var waitedMs = 0L
        while (true) {
            val current = probe.currentForegroundPackage()?.trim().orEmpty()
            if (current.isNotEmpty() && current != blockedPackage) return true
            if (waitedMs >= timeoutMs) return false
            delay(pollIntervalMs)
            waitedMs += pollIntervalMs
        }
    }
}
