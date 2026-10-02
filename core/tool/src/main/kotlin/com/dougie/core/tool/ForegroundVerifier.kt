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
}
