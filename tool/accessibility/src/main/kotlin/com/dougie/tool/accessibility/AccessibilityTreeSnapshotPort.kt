package com.dougie.tool.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.dougie.core.tool.TreeSnapshot
import com.dougie.core.tool.TreeSnapshotPort

/**
 * Reads the active window's visible text (node text + contentDescription) as a
 * verification-layer snapshot. The snapshot stays inside the consuming tool's call
 * stack — it is never logged, persisted, or placed in any result JSON.
 */
class AccessibilityTreeSnapshotPort(
    private val service: () -> AccessibilityService?,
) : TreeSnapshotPort {
    override suspend fun snapshot(): TreeSnapshot? {
        val root = service()?.rootInActiveWindow ?: return null
        val texts = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return TreeSnapshot(texts)
    }

    companion object {
        private const val MAX_NODES = 300
    }
}
