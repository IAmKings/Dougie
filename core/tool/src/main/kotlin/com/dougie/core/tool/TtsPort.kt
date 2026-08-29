package com.dougie.core.tool

import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.delay

enum class TtsOutcome {
    SPOKEN,
    FAILED,
    NETWORK,
    STOPPED,
}

interface TtsEngine {
    fun isReady(): Boolean
    suspend fun speak(text: String): TtsOutcome
    fun stop() {}
}

data class TtsSpeakResult(
    val ok: Boolean,
    val backend: String,
    val error: String? = null,
    val stopped: Boolean = false,
)

object UnwiredTtsEngine : TtsEngine {
    override fun isReady(): Boolean = false
    override suspend fun speak(text: String): TtsOutcome = TtsOutcome.FAILED
}

class FakeTtsEngine(
    var ready: Boolean = false,
    var outcome: TtsOutcome = TtsOutcome.SPOKEN,
    var hangMs: Long = 0,
) : TtsEngine {
    val spoken = mutableListOf<String>()

    @Volatile
    var stopped: Boolean = false
        private set

    override fun isReady(): Boolean = ready

    override fun stop() {
        stopped = true
    }

    override suspend fun speak(text: String): TtsOutcome {
        spoken += text
        if (hangMs > 0L) {
            val steps = (hangMs / 10L).toInt().coerceAtLeast(1)
            repeat(steps) {
                if (stopped) return TtsOutcome.STOPPED
                delay(10)
            }
        }
        if (stopped) return TtsOutcome.STOPPED
        return outcome
    }
}

class PreferOfflineTtsPort(
    private val offline: TtsEngine,
    private val fallback: TtsEngine,
) {
    fun isOfflineReady(): Boolean = offline.isReady()

    fun stop() {
        offline.stop()
        fallback.stop()
    }

    suspend fun speak(text: String): TtsSpeakResult {
        if (offline.isReady()) {
            return map(offline.speak(text), BACKEND_OFFLINE)
        }
        if (text.length > MAX_SYSTEM_CHARS) {
            return TtsSpeakResult(ok = false, backend = BACKEND_SYSTEM, error = UserFacingErrors.TTS_TOO_LONG)
        }
        return map(fallback.speak(text), BACKEND_SYSTEM)
    }

    suspend fun speakFinal(text: String): TtsSpeakResult {
        if (!offline.isReady()) {
            return TtsSpeakResult(
                ok = false,
                backend = BACKEND_OFFLINE,
                error = UserFacingErrors.TTS_REPLY_UNAVAILABLE,
            )
        }
        return when (val outcome = offline.speak(TtsSpeakText.forOffline(text))) {
            TtsOutcome.SPOKEN -> TtsSpeakResult(ok = true, backend = BACKEND_OFFLINE)
            TtsOutcome.STOPPED -> TtsSpeakResult(ok = true, backend = BACKEND_OFFLINE, stopped = true)
            TtsOutcome.NETWORK, TtsOutcome.FAILED -> TtsSpeakResult(
                ok = false,
                backend = BACKEND_OFFLINE,
                error = UserFacingErrors.TTS_REPLY_UNAVAILABLE,
            )
        }
    }

    private fun map(outcome: TtsOutcome, backend: String): TtsSpeakResult = when (outcome) {
        TtsOutcome.SPOKEN -> TtsSpeakResult(ok = true, backend = backend)
        TtsOutcome.STOPPED -> TtsSpeakResult(
            ok = false,
            backend = backend,
            error = UserFacingErrors.TTS_FAILED,
            stopped = true,
        )
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
