package com.dougie.feature.chat

import com.dougie.core.model.UserFacingErrors

/** In-app Dougie mark from local vs remote chat LLM state. Launcher stays [LOCAL]. */
enum class IntelligenceMark {
    /** No chat LLM configured, or a remote call actually failed. */
    NOOB,

    /** Remote not configured; a local chat LLM can stand in. */
    LOCAL,

    /** Remote provider is configured and currently usable. */
    SUPER,
}

private val REMOTE_UNUSABLE = setOf(
    UserFacingErrors.EGRESS_BLOCKED,
    UserFacingErrors.MISSING_API_KEY,
    UserFacingErrors.LLM_FAILED,
    UserFacingErrors.NETWORK_FAILED,
    UserFacingErrors.LLM_TIMEOUT,
)

private val REMOTE_CALL_FAILED = setOf(
    UserFacingErrors.LLM_FAILED,
    UserFacingErrors.NETWORK_FAILED,
    UserFacingErrors.LLM_TIMEOUT,
)

/**
 * [localLlmReady] is a local **chat** LLM only. Intent GGUF (and ASR/TTS) must not
 * be passed as true.
 */
fun intelligenceMark(
    allowCloud: Boolean,
    apiKeyConfigured: Boolean,
    localLlmReady: Boolean,
    failedLastError: String? = null,
): IntelligenceMark {
    val remoteConfigured = allowCloud && apiKeyConfigured
    val remoteUsable = remoteConfigured && failedLastError !in REMOTE_UNUSABLE
    if (remoteUsable) return IntelligenceMark.SUPER
    if (failedLastError in REMOTE_CALL_FAILED) return IntelligenceMark.NOOB
    if (localLlmReady) return IntelligenceMark.LOCAL
    return IntelligenceMark.NOOB
}
