package com.dougie.tool.system

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AsrModelLayout
import com.dougie.core.tool.SpeechUtterance
import com.dougie.core.tool.TtsModelLayout
import com.dougie.core.tool.TtsOutcome
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import kotlin.math.max

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

    fun speak(modelDir: File, text: String): TtsOutcome {
        if (!isAvailable()) return TtsOutcome.FAILED
        var tts: OfflineTts? = null
        return try {
            val dict = File(modelDir, TtsModelLayout.DICT_DIR)
            tts = OfflineTts(
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = File(modelDir, TtsModelLayout.MODEL_FILE).absolutePath,
                            lexicon = File(modelDir, TtsModelLayout.LEXICON_FILE).absolutePath,
                            tokens = File(modelDir, TtsModelLayout.TOKENS_FILE).absolutePath,
                            dictDir = if (dict.isDirectory) dict.absolutePath else "",
                        ),
                        numThreads = 1,
                        debug = false,
                        provider = "cpu",
                    ),
                ),
            )
            val audio = tts.generate(text, sid = 0, speed = 1.0f)
            if (audio.samples.isEmpty()) {
                TtsOutcome.FAILED
            } else {
                playPcm(audio.samples, audio.sampleRate)
                TtsOutcome.SPOKEN
            }
        } catch (_: Throwable) {
            TtsOutcome.FAILED
        } finally {
            tts?.release()
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val pcm = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBuf, pcm.size * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                val written = track.write(pcm, offset, pcm.size - offset)
                if (written <= 0) break
                offset += written
            }
            val rate = sampleRate.coerceAtLeast(1)
            val deadlineMs = (pcm.size * 1000L) / rate + 200L
            var waited = 0L
            while (
                waited < deadlineMs &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition < pcm.size
            ) {
                Thread.sleep(20L)
                waited += 20L
            }
            track.stop()
        } finally {
            track.release()
        }
    }
}
