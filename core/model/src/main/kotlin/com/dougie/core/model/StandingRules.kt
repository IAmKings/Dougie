package com.dougie.core.model

/** Local standing rules injected after the default identity. Do not log the text. */
object StandingRules {
    const val MAX_CODE_POINTS = 240

    /** Keep spaces while editing. Drops only past the code-point cap. */
    fun limit(raw: String): String {
        if (raw.isEmpty()) return ""
        val count = raw.codePointCount(0, raw.length)
        if (count <= MAX_CODE_POINTS) return raw
        return raw.substring(0, raw.offsetByCodePoints(0, MAX_CODE_POINTS))
    }

    /** Text that may enter a prompt: capped, then trimmed. Blank stays empty. */
    fun clamp(raw: String): String = limit(raw).trim()
}
