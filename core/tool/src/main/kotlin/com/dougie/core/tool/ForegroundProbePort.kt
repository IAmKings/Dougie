package com.dougie.core.tool

/**
 * Observer primitive of the verification layer: which package is in the foreground right now.
 * Returns null when unknown (e.g. Play builds without the accessibility service).
 */
fun interface ForegroundProbePort {
    fun currentForegroundPackage(): String?
}

class FakeForegroundProbe(
    var current: String? = null,
) : ForegroundProbePort {
    private val script = ArrayDeque<String?>()

    var callCount = 0
        private set

    fun enqueue(vararg values: String?) {
        for (value in values) script.addLast(value)
    }

    override fun currentForegroundPackage(): String? {
        callCount++
        return if (script.isNotEmpty()) script.removeFirst() else current
    }
}
