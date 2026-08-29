package com.dougie.core.tool

/**
 * Curated `sid` values for catalog VITS `vits-zh-hf-fanchen-C` (187 speakers).
 * Sherpa examples use 0 (default), 14, and 100. Unknown ids clamp to default.
 */
data class TtsVoiceOption(
    val sid: Int,
    val label: String,
)

object TtsVoices {
    val OPTIONS: List<TtsVoiceOption> = listOf(
        TtsVoiceOption(sid = 0, label = "默认"),
        TtsVoiceOption(sid = 14, label = "音色一"),
        TtsVoiceOption(sid = 100, label = "音色二"),
    )

    fun clamp(sid: Int): Int = OPTIONS.firstOrNull { it.sid == sid }?.sid ?: 0

    fun label(sid: Int): String = OPTIONS.first { it.sid == clamp(sid) }.label
}
