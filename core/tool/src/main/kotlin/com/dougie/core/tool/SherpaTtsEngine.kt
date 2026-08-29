package com.dougie.core.tool

import java.io.File

object TtsModelLayout {
    const val DIR = "models/tts"
    const val MODEL_FILE = "model.onnx"
    const val TOKENS_FILE = "tokens.txt"
    const val LEXICON_FILE = "lexicon.txt"
    const val DICT_DIR = "dict"

    fun isPresent(modelDir: File): Boolean {
        val model = File(modelDir, MODEL_FILE)
        val tokens = File(modelDir, TOKENS_FILE)
        val lexicon = File(modelDir, LEXICON_FILE)
        return model.isFile && model.length() > 0L &&
            tokens.isFile && tokens.length() > 0L &&
            lexicon.isFile && lexicon.length() > 0L
    }
}

class SherpaTtsEngine(
    private val modelDir: File,
    private val nativeAvailable: () -> Boolean,
    private val speakNative: (File, String) -> TtsOutcome,
    private val stopNative: () -> Unit = {},
) : TtsEngine {
    override fun isReady(): Boolean = TtsModelLayout.isPresent(modelDir) && nativeAvailable()

    override suspend fun speak(text: String): TtsOutcome = try {
        speakNative(modelDir, text)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        TtsOutcome.FAILED
    }

    override fun stop() = stopNative()
}
