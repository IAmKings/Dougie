package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SpeechInputToolTest {
    @Test
    fun backgroundDoesNotListen() = runTest {
        val port = FakeSpeechPort(foreground = false)
        val result = SpeechInputTool(port).execute("{}", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.SPEECH_NOT_FOREGROUND, result.error)
        assertTrue(result.isFatal)
        assertEquals(0, port.listenCount)
    }

    @Test
    fun missingModelDoesNotListen() = runTest {
        val port = FakeSpeechPort(modelPresent = false)
        val result = SpeechInputTool(port).execute("{}", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.SPEECH_MODEL_MISSING, result.error)
        assertEquals(0, port.listenCount)
    }

    @Test
    fun engineNotReadyDoesNotListen() = runTest {
        val port = FakeSpeechPort(engineReady = false)
        val result = SpeechInputTool(port).execute("{}", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.SPEECH_ENGINE_NOT_READY, result.error)
        assertEquals(0, port.listenCount)
    }

    @Test
    fun readyListenReturnsTextOnly() = runTest {
        val port = FakeSpeechPort(transcript = "现在几点")
        val result = SpeechInputTool(port).execute("{}", ToolContext("t", "c"))
        assertFalse(result.isFatal)
        assertTrue(result.json.contains("\"ok\":true"))
        assertTrue(result.json.contains("现在几点"))
        assertFalse(result.json.contains("pcm"))
        assertFalse(result.json.contains("base64"))
        assertFalse(result.json.contains("audio"))
        assertEquals(1, port.listenCount)
    }

    @Test
    fun sessionReadyCapturesOnceWithoutAudioJson() = runTest {
        val recorder = FakeSpeechRecorder()
        val engine = FakeSpeechEngine(transcript = "现在几点")
        val tool = SpeechInputTool(
            SpeechSession(
                foregroundCheck = { true },
                modelCheck = { true },
                engine = engine,
                recorder = recorder,
            ),
        )
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertFalse(result.isFatal)
        assertTrue(result.json.contains("现在几点"))
        assertFalse(result.json.contains("pcm"))
        assertFalse(result.json.contains("base64"))
        assertFalse(result.json.contains("audio"))
        assertEquals(1, recorder.captureCount)
        assertEquals(1, engine.transcribeCount)
        assertEquals(2, engine.lastUtterance!!.samples.size)
    }

    @Test
    fun sessionGatesDoNotCapture() = runTest {
        val recorder = FakeSpeechRecorder()
        val engine = FakeSpeechEngine(ready = false)
        val tool = SpeechInputTool(
            SpeechSession(
                foregroundCheck = { true },
                modelCheck = { true },
                engine = engine,
                recorder = recorder,
            ),
        )
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.SPEECH_ENGINE_NOT_READY, result.error)
        assertEquals(0, recorder.captureCount)
        assertEquals(0, engine.transcribeCount)
    }

    @Test
    fun emptyUtteranceFailsWithoutAudioJson() = runTest {
        val recorder = FakeSpeechRecorder(SpeechUtterance(floatArrayOf(), 16_000))
        val engine = FakeSpeechEngine()
        val tool = SpeechInputTool(
            SpeechSession(
                foregroundCheck = { true },
                modelCheck = { true },
                engine = engine,
                recorder = recorder,
            ),
        )
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.SPEECH_EMPTY, result.error)
        assertTrue(result.isFatal)
        assertEquals(1, recorder.captureCount)
        assertEquals(0, engine.transcribeCount)
        assertFalse(result.json.contains("pcm"))
    }

    @Test
    fun asrModelLayoutRequiresBothFiles() {
        val dir = Files.createTempDirectory("asr-layout").toFile()
        try {
            assertFalse(AsrModelLayout.isPresent(dir))
            File(dir, AsrModelLayout.MODEL_FILE).writeText("x")
            assertFalse(AsrModelLayout.isPresent(dir))
            File(dir, AsrModelLayout.TOKENS_FILE).writeText("a")
            assertTrue(AsrModelLayout.isPresent(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sherpaEngineNeedsModelAndNative() = runTest {
        val dir = Files.createTempDirectory("sherpa-engine").toFile()
        try {
            assertFalse(
                SherpaSpeechEngine(
                    modelDir = dir,
                    nativeAvailable = { true },
                    decode = { _, _ -> "hi" },
                ).isReady(),
            )
            File(dir, AsrModelLayout.MODEL_FILE).writeText("x")
            File(dir, AsrModelLayout.TOKENS_FILE).writeText("a")
            var decoded = 0
            val blocked = SherpaSpeechEngine(
                modelDir = dir,
                nativeAvailable = { false },
                decode = { _, _ -> error("decode") },
            )
            assertFalse(blocked.isReady())
            val ready = SherpaSpeechEngine(
                modelDir = dir,
                nativeAvailable = { true },
                decode = { _, utterance ->
                    decoded += 1
                    assertEquals(2, utterance.samples.size)
                    "现在几点"
                },
            )
            assertTrue(ready.isReady())
            val recorder = FakeSpeechRecorder()
            val result = SpeechInputTool(
                SpeechSession(
                    foregroundCheck = { true },
                    modelCheck = { true },
                    engine = ready,
                    recorder = recorder,
                ),
            ).execute("{}", ToolContext("t", "c"))
            assertTrue(result.json.contains("现在几点"))
            assertFalse(result.json.contains("pcm"))
            assertEquals(1, decoded)
            assertEquals(1, recorder.captureCount)
        } finally {
            dir.deleteRecursively()
        }
    }
}
