package com.dougie.tool.system

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.dougie.core.tool.SpeechRecorder
import com.dougie.core.tool.SpeechUtterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRecordSpeechRecorder(
    private val onUsed: () -> Unit = {},
    private val durationMs: Int = DEFAULT_DURATION_MS,
    private val sampleRate: Int = SAMPLE_RATE,
) : SpeechRecorder {
    override suspend fun capture(): SpeechUtterance = withContext(Dispatchers.IO) {
        onUsed()
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            return@withContext SpeechUtterance(floatArrayOf(), sampleRate)
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext SpeechUtterance(floatArrayOf(), sampleRate)
        }
        val shorts = ShortArray(sampleRate * durationMs / 1000)
        try {
            record.startRecording()
            var offset = 0
            while (offset < shorts.size) {
                val read = record.read(shorts, offset, shorts.size - offset)
                if (read <= 0) break
                offset += read
            }
            val floats = FloatArray(offset) { index -> shorts[index] / 32768.0f }
            SpeechUtterance(samples = floats, sampleRate = sampleRate)
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val DEFAULT_DURATION_MS = 3_000
    }
}
