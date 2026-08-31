package com.dougie.app

import android.content.Context
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AsrModelLayout
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.OnnxIntentEngine
import com.dougie.core.tool.SherpaSpeechEngine
import com.dougie.core.tool.SpeechUtterance
import com.dougie.core.tool.TtsModelLayout
import com.dougie.feature.settings.OfflineModelProbe
import com.dougie.feature.settings.ProbeResult
import com.dougie.tool.system.IntentOrtJni
import com.dougie.tool.system.SherpaJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppOfflineModelProbe {
    fun create(context: Context): OfflineModelProbe = OfflineModelProbe { id ->
        withContext(Dispatchers.Default) {
            when (id) {
                "asr" -> probeAsr(context)
                "tts" -> probeTts(context)
                IntentModelLayout.ID -> probeIntent(context)
                else -> ProbeResult(ok = false, message = UserFacingErrors.TOOL_FAILED)
            }
        }
    }

    private suspend fun probeAsr(context: Context): ProbeResult {
        val modelDir = File(context.filesDir, AsrModelLayout.DIR)
        if (!AsrModelLayout.isPresent(modelDir)) {
            throw AgentException(UserFacingErrors.SPEECH_MODEL_MISSING)
        }
        val engine = SherpaSpeechEngine(
            modelDir = modelDir,
            nativeAvailable = SherpaJni::isAvailable,
            decode = { dir, utterance -> SherpaJni.decode(dir, utterance) },
        )
        if (!engine.isReady()) {
            throw AgentException(UserFacingErrors.SPEECH_ENGINE_NOT_READY)
        }
        val silence = SpeechUtterance(
            samples = FloatArray(4_800),
            sampleRate = 16_000,
        )
        engine.transcribe(silence)
        return ProbeResult(ok = true, message = UserFacingErrors.MODEL_PROBE_ASR_OK)
    }

    private fun probeTts(context: Context): ProbeResult {
        val modelDir = File(context.filesDir, TtsModelLayout.DIR)
        if (!TtsModelLayout.isPresent(modelDir)) {
            throw AgentException(UserFacingErrors.TTS_FAILED)
        }
        if (!SherpaJni.isAvailable()) {
            throw AgentException(UserFacingErrors.TTS_FAILED)
        }
        val samples = SherpaJni.generatePcm(modelDir, "测")
        if (samples.isEmpty()) {
            throw AgentException(UserFacingErrors.TTS_FAILED)
        }
        return ProbeResult(ok = true, message = UserFacingErrors.MODEL_PROBE_TTS_OK)
    }

    private suspend fun probeIntent(context: Context): ProbeResult {
        val modelDir = File(context.filesDir, IntentModelLayout.DIR)
        if (!IntentModelLayout.isPresent(modelDir)) {
            throw AgentException(UserFacingErrors.INTENT_MODEL_MISSING)
        }
        val engine = OnnxIntentEngine(
            modelDir = modelDir,
            nativeAvailable = IntentOrtJni::isAvailable,
            infer = { dir, features -> IntentOrtJni.infer(dir, features) },
            inferTokens = { dir, ids, mask -> IntentOrtJni.inferTokens(dir, ids, mask) },
        )
        if (!engine.isReady()) {
            throw AgentException(UserFacingErrors.INTENT_ENGINE_NOT_READY)
        }
        engine.classify("现在几点")
        return ProbeResult(ok = true, message = UserFacingErrors.MODEL_PROBE_INTENT_OK)
    }
}
