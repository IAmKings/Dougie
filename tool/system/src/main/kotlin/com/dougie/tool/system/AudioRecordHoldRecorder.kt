package com.dougie.tool.system

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.dougie.core.tool.HoldSpeechRecorder
import com.dougie.core.tool.SpeechHold
import com.dougie.core.tool.SpeechUtterance
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecordHoldRecorder(
    private val onUsed: () -> Unit = {},
    private val maxMs: Int = SpeechHold.MAX_MS,
    private val sampleRate: Int = AudioRecordSpeechRecorder.SAMPLE_RATE,
) : HoldSpeechRecorder {
    private val lock = Any()
    private val stopRequested = AtomicBoolean(false)
    private var pending: CompletableDeferred<SpeechUtterance>? = null
    private var worker: Thread? = null

    override fun start(): Boolean {
        synchronized(lock) {
            if (pending != null) return false
            stopRequested.set(false)
            val deferred = CompletableDeferred<SpeechUtterance>()
            pending = deferred
            worker = Thread(
                {
                    onUsed()
                    deferred.complete(recordUntilStop())
                },
                "dougie-hold-mic",
            ).also { it.start() }
            return true
        }
    }

    override suspend fun stop(): SpeechUtterance {
        stopRequested.set(true)
        val deferred: CompletableDeferred<SpeechUtterance>
        synchronized(lock) {
            deferred = pending ?: return SpeechUtterance(floatArrayOf(), sampleRate)
        }
        val utterance = deferred.await()
        synchronized(lock) {
            pending = null
            worker = null
        }
        return utterance
    }

    private fun recordUntilStop(): SpeechUtterance {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            return SpeechUtterance(floatArrayOf(), sampleRate)
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
            return SpeechUtterance(floatArrayOf(), sampleRate)
        }
        val maxSamples = (sampleRate * maxMs / 1000).coerceAtLeast(1)
        val collected = ArrayList<Short>(maxSamples)
        val chunk = ShortArray(minBuffer)
        try {
            record.startRecording()
            while (collected.size < maxSamples && !stopRequested.get()) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) break
                val remain = maxSamples - collected.size
                val take = minOf(read, remain)
                for (i in 0 until take) {
                    collected.add(chunk[i])
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        val floats = FloatArray(collected.size) { index -> collected[index] / 32768.0f }
        return SpeechUtterance(samples = floats, sampleRate = sampleRate)
    }
}
