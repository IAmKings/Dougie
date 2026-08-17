package com.dougie.tool.system

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.dougie.core.tool.TtsEngine
import com.dougie.core.tool.TtsOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class AndroidSystemTtsEngine(
    context: Context,
) : TtsEngine {
    private val appContext = context.applicationContext

    override fun isReady(): Boolean = true

    override suspend fun speak(text: String): TtsOutcome = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            fun finish(engine: TextToSpeech, outcome: TtsOutcome) {
                engine.shutdown()
                if (cont.isActive) cont.resume(outcome)
            }
            tts = TextToSpeech(appContext) { status ->
                val engine = tts
                if (engine == null || !cont.isActive) {
                    engine?.shutdown()
                    return@TextToSpeech
                }
                if (status != TextToSpeech.SUCCESS) {
                    finish(engine, TtsOutcome.FAILED)
                    return@TextToSpeech
                }
                if (engine.setLanguage(Locale.CHINA) < TextToSpeech.LANG_AVAILABLE) {
                    finish(engine, TtsOutcome.FAILED)
                    return@TextToSpeech
                }
                if (requiresNetwork(engine.voice)) {
                    finish(engine, TtsOutcome.NETWORK)
                    return@TextToSpeech
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        finish(engine, TtsOutcome.SPOKEN)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finish(engine, TtsOutcome.FAILED)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        finish(engine, TtsOutcome.FAILED)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        finish(engine, TtsOutcome.FAILED)
                    }
                })
                val started = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
                if (started != TextToSpeech.SUCCESS) {
                    finish(engine, TtsOutcome.FAILED)
                }
            }
            cont.invokeOnCancellation { tts?.shutdown() }
        }
    }

    companion object {
        private const val UTTERANCE_ID = "dougie-speech-output"

        private fun requiresNetwork(voice: Voice?): Boolean =
            voice != null && voice.isNetworkConnectionRequired
    }
}
