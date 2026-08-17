package com.dougie.tool.system

import android.content.Context
import com.dougie.core.tool.AsrModelLayout
import com.dougie.core.tool.SpeechEngine
import com.dougie.core.tool.SpeechPort
import com.dougie.core.tool.SpeechRecorder
import com.dougie.core.tool.SpeechSession
import com.dougie.core.tool.UnwiredSpeechEngine
import java.io.File

class AndroidSpeechPort(
    context: Context,
    isForeground: () -> Boolean,
    onUsed: () -> Unit = {},
    engine: SpeechEngine = UnwiredSpeechEngine,
    recorder: SpeechRecorder = AudioRecordSpeechRecorder(onUsed = onUsed),
) : SpeechPort {
    private val session = SpeechSession(
        foregroundCheck = isForeground,
        modelCheck = { AsrModelLayout.isPresent(File(context.applicationContext.filesDir, AsrModelLayout.DIR)) },
        engine = engine,
        recorder = recorder,
    )

    override fun isAppForeground(): Boolean = session.isAppForeground()
    override fun isModelPresent(): Boolean = session.isModelPresent()
    override fun isEngineReady(): Boolean = session.isEngineReady()
    override suspend fun listen(): String = session.listen()
}
