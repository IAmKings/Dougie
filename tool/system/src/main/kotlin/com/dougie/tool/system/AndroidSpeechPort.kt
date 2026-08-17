package com.dougie.tool.system

import android.content.Context
import com.dougie.core.tool.AsrModelLayout
import com.dougie.core.tool.SherpaSpeechEngine
import com.dougie.core.tool.SpeechEngine
import com.dougie.core.tool.SpeechPort
import com.dougie.core.tool.SpeechRecorder
import com.dougie.core.tool.SpeechSession
import java.io.File

class AndroidSpeechPort(
    context: Context,
    isForeground: () -> Boolean,
    onUsed: () -> Unit = {},
    engine: SpeechEngine? = null,
    recorder: SpeechRecorder = AudioRecordSpeechRecorder(onUsed = onUsed),
) : SpeechPort {
    private val modelDir = File(context.applicationContext.filesDir, AsrModelLayout.DIR)
    private val session = SpeechSession(
        foregroundCheck = isForeground,
        modelCheck = { AsrModelLayout.isPresent(modelDir) },
        engine = engine ?: SherpaSpeechEngine(
            modelDir = modelDir,
            nativeAvailable = { SherpaJni.isAvailable() },
            decode = { dir, utterance -> SherpaJni.decode(dir, utterance) },
        ),
        recorder = recorder,
    )

    override fun isAppForeground(): Boolean = session.isAppForeground()
    override fun isModelPresent(): Boolean = session.isModelPresent()
    override fun isEngineReady(): Boolean = session.isEngineReady()
    override suspend fun listen(): String = session.listen()
}
