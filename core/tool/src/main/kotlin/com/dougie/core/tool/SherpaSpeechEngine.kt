package com.dougie.core.tool

import java.io.File

class SherpaSpeechEngine(
    private val modelDir: File,
    private val nativeAvailable: () -> Boolean,
    private val decode: (File, SpeechUtterance) -> String,
) : SpeechEngine {
    override fun isReady(): Boolean = AsrModelLayout.isPresent(modelDir) && nativeAvailable()

    override suspend fun transcribe(utterance: SpeechUtterance): String {
        return decode(modelDir, utterance)
    }
}
