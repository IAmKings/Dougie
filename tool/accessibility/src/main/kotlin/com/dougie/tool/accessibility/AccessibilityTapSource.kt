package com.dougie.tool.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.dougie.core.tool.TapCandidate
import com.dougie.core.tool.TapSourcePort

/**
 * Extracts tappable-node candidates from the active window for tap-by-text. Snapshots
 * (including bounds) stay inside the consuming tool's call stack.
 */
class AccessibilityTapSource(
    private val service: () -> AccessibilityService?,
) : TapSourcePort {
    override suspend fun candidates(): List<TapCandidate> {
        val root = service()?.rootInActiveWindow ?: return emptyList()
        val out = ArrayList<TapCandidate>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                out.add(
                    TapCandidate(
                        text = text,
                        desc = desc,
                        clickable = node.isClickable,
                        centerX = bounds.centerX(),
                        centerY = bounds.centerY(),
                    ),
                )
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return out
    }

    companion object {
        private const val MAX_NODES = 300
    }
}
