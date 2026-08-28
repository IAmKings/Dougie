package com.dougie.core.tool

import java.io.File

object AsrModelLayout {
    const val DIR = "models/asr"
    const val MODEL_FILE = "model.int8.onnx"
    const val TOKENS_FILE = "tokens.txt"

    fun isPresent(modelDir: File): Boolean {
        val model = File(modelDir, MODEL_FILE)
        val tokens = File(modelDir, TOKENS_FILE)
        return model.isFile && model.length() > 0L && tokens.isFile && tokens.length() > 0L
    }
}

data class SpeechUtterance(
    val samples: FloatArray,
    val sampleRate: Int,
)

interface SpeechRecorder {
    suspend fun capture(): SpeechUtterance
}

object SpeechHold {
    const val MAX_MS = 15_000
}

interface HoldSpeechRecorder {
    fun start(): Boolean
    suspend fun stop(): SpeechUtterance
}

interface SpeechEngine {
    fun isReady(): Boolean
    suspend fun transcribe(utterance: SpeechUtterance): String
}

object UnwiredSpeechEngine : SpeechEngine {
    override fun isReady(): Boolean = false
    override suspend fun transcribe(utterance: SpeechUtterance): String {
        error("offline speech engine is not wired")
    }
}

class FakeSpeechRecorder(
    var utterance: SpeechUtterance = SpeechUtterance(floatArrayOf(0.1f, -0.1f), 16_000),
) : SpeechRecorder {
    var captureCount: Int = 0
        private set

    override suspend fun capture(): SpeechUtterance {
        captureCount += 1
        return utterance
    }
}

class FakeHoldSpeechRecorder(
    var utterance: SpeechUtterance = SpeechUtterance(floatArrayOf(0.1f, -0.1f), 16_000),
) : HoldSpeechRecorder {
    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set
    private var running: Boolean = false

    override fun start(): Boolean {
        if (running) return false
        running = true
        startCount += 1
        return true
    }

    override suspend fun stop(): SpeechUtterance {
        running = false
        stopCount += 1
        return utterance
    }
}

class FakeSpeechEngine(
    var ready: Boolean = true,
    var transcript: String = "现在几点",
) : SpeechEngine {
    var lastUtterance: SpeechUtterance? = null
        private set
    var transcribeCount: Int = 0
        private set

    override fun isReady(): Boolean = ready

    override suspend fun transcribe(utterance: SpeechUtterance): String {
        transcribeCount += 1
        lastUtterance = utterance
        return transcript
    }
}

class SpeechSession(
    private val foregroundCheck: () -> Boolean,
    private val modelCheck: () -> Boolean,
    private val engine: SpeechEngine,
    private val recorder: SpeechRecorder,
) : SpeechPort {
    override fun isAppForeground(): Boolean = foregroundCheck()
    override fun isModelPresent(): Boolean = modelCheck()
    override fun isEngineReady(): Boolean = engine.isReady()

    override suspend fun listen(): String {
        return transcribe(recorder.capture())
    }

    suspend fun transcribe(utterance: SpeechUtterance): String {
        if (utterance.samples.isEmpty()) {
            return ""
        }
        return engine.transcribe(utterance)
    }
}
