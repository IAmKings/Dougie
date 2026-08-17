package com.dougie.tool.system

import android.content.Context
import com.dougie.core.tool.SpeechPort
import java.io.File

class AndroidSpeechPort(
    context: Context,
    private val isForeground: () -> Boolean,
    private val onUsed: () -> Unit = {},
) : SpeechPort {
    private val modelFile = File(context.applicationContext.filesDir, MODEL_RELATIVE_PATH)

    override fun isAppForeground(): Boolean = isForeground()

    override fun isModelPresent(): Boolean = modelFile.isFile && modelFile.length() > 0L

    override fun isEngineReady(): Boolean = false

    override suspend fun listen(): String {
        onUsed()
        error("offline speech engine is not wired")
    }

    companion object {
        const val MODEL_RELATIVE_PATH = "models/asr/encoder.onnx"
    }
}
