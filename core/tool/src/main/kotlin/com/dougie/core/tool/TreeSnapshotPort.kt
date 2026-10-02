package com.dougie.core.tool

/**
 * State-extractor primitive of the verification layer: the visible text of the active
 * window (node text + contentDescription). The snapshot lives only inside the consuming
 * tool's call stack — it must never enter tool JSON, traces, logs, or LLM context.
 */
data class TreeSnapshot(val texts: List<String>)

/**
 * Returns the current active-window snapshot, or null when there is no observer
 * (channel without accessibility) or no active window to read.
 */
fun interface TreeSnapshotPort {
    suspend fun snapshot(): TreeSnapshot?
}

class FakeTreeSnapshotPort(
    var current: TreeSnapshot? = null,
) : TreeSnapshotPort {
    private val script = ArrayDeque<TreeSnapshot?>()

    var callCount = 0
        private set

    fun enqueue(vararg values: TreeSnapshot?) {
        for (value in values) script.addLast(value)
    }

    override suspend fun snapshot(): TreeSnapshot? {
        callCount++
        return if (script.isNotEmpty()) script.removeFirst() else current
    }
}
