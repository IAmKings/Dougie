package com.dougie.tool.system

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AsrModelLayout
import com.dougie.core.tool.SpeechUtterance
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

object SherpaJni {
    @Volatile
    private var loaded: Boolean? = null

    fun isAvailable(): Boolean {
        val cached = loaded
        if (cached != null) return cached
        val ok = runCatching { System.loadLibrary("sherpa-onnx-jni") }.isSuccess
        loaded = ok
        return ok
    }

    fun decode(modelDir: File, utterance: SpeechUtterance): String {
        if (!isAvailable()) {
            throw AgentException(UserFacingErrors.SPEECH_ENGINE_NOT_READY)
        }
        val model = File(modelDir, AsrModelLayout.MODEL_FILE).absolutePath
        val tokens = File(modelDir, AsrModelLayout.TOKENS_FILE).absolutePath
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = utterance.sampleRate, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    paraformer = OfflineParaformerModelConfig(model = model),
                    tokens = tokens,
                    modelType = "paraformer",
                    numThreads = 1,
                    provider = "cpu",
                ),
            ),
        )
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(utterance.samples, utterance.sampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } catch (_: Throwable) {
            throw AgentException(UserFacingErrors.TOOL_FAILED)
        } finally {
            stream.release()
            recognizer.release()
        }
    }
}
