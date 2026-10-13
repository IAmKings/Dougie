package com.dougie.core.tool

/**
 * A tappable-node snapshot extracted from the accessibility tree on device: the visible
 * text / content-description, whether the node itself is clickable, and the screen
 * center of its bounds.
 */
data class TapCandidate(
    val text: String,
    val desc: String,
    val clickable: Boolean,
    val centerX: Int,
    val centerY: Int,
)

/** Source of on-device tappable candidates for [TapTargetSelector]. */
fun interface TapSourcePort {
    suspend fun candidates(): List<TapCandidate>
}

class FakeTapSourcePort(
    var current: List<TapCandidate> = emptyList(),
) : TapSourcePort {
    override suspend fun candidates(): List<TapCandidate> = current
}

/**
 * Deterministic selection of a tap target by text description. Matching is
 * whitespace-insensitive containment over text and content-desc; clickable nodes win;
 * ties go to the LAST match in document order, which approximates the lower part of the
 * screen (fits "底部的 tab" style requests without the model knowing coordinates).
 */
object TapTargetSelector {
    fun select(candidates: List<TapCandidate>, needle: String): TapCandidate? {
        val key = compact(needle)
        if (key.isEmpty()) return null
        val matches = candidates.filter { candidate ->
            compact(candidate.text).contains(key) || compact(candidate.desc).contains(key)
        }
        if (matches.isEmpty()) return null
        val clickable = matches.filter { it.clickable }
        val pool = clickable.ifEmpty { matches }
        return pool.last()
    }

    private fun compact(value: String): String = value.filter { !it.isWhitespace() }
}
