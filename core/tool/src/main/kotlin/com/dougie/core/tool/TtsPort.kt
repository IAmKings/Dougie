package com.dougie.core.tool

import com.dougie.core.model.UserFacingErrors

enum class TtsOutcome {
    SPOKEN,
    FAILED,
    NETWORK,
}

interface TtsEngine {
    fun isReady(): Boolean
    suspend fun speak(text: String): TtsOutcome
}

data class TtsSpeakResult(
    val ok: Boolean,
    val backend: String,
    val error: String? = null,
)

object UnwiredTtsEngine : TtsEngine {
    override fun isReady(): Boolean = false
    override suspend fun speak(text: String): TtsOutcome = TtsOutcome.FAILED
}

class FakeTtsEngine(
    var ready: Boolean = false,
    var outcome: TtsOutcome = TtsOutcome.SPOKEN,
) : TtsEngine {
    val spoken = mutableListOf<String>()

    override fun isReady(): Boolean = ready

    override suspend fun speak(text: String): TtsOutcome {
        spoken += text
        return outcome
    }
}

class PreferOfflineTtsPort(
    private val offline: TtsEngine,
    private val fallback: TtsEngine,
) {
    suspend fun speak(text: String): TtsSpeakResult {
        if (offline.isReady()) {
            return map(offline.speak(text), BACKEND_OFFLINE)
        }
        if (text.length > MAX_SYSTEM_CHARS) {
            return TtsSpeakResult(ok = false, backend = BACKEND_SYSTEM, error = UserFacingErrors.TTS_TOO_LONG)
        }
        return map(fallback.speak(text), BACKEND_SYSTEM)
    }

    private fun map(outcome: TtsOutcome, backend: String): TtsSpeakResult = when (outcome) {
        TtsOutcome.SPOKEN -> TtsSpeakResult(ok = true, backend = backend)
        TtsOutcome.NETWORK -> TtsSpeakResult(
            ok = false,
            backend = backend,
            error = UserFacingErrors.TTS_NETWORK,
        )
        TtsOutcome.FAILED -> TtsSpeakResult(
            ok = false,
            backend = backend,
            error = UserFacingErrors.TTS_FAILED,
        )
    }

    companion object {
        const val BACKEND_OFFLINE = "offline"
        const val BACKEND_SYSTEM = "system"
        const val MAX_SYSTEM_CHARS = 80
    }
}
