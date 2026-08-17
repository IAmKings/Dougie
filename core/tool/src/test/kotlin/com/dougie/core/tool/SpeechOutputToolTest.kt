package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SpeechOutputToolTest {
    @Test
    fun prefersOfflineWhenReady() = runTest {
        val offline = FakeTtsEngine(ready = true)
        val fallback = FakeTtsEngine(ready = true)
        val tool = SpeechOutputTool(PreferOfflineTtsPort(offline, fallback))
        val result = tool.execute("""{"text":"你好"}""", ToolContext("t", "c"))
        assertTrue(result.json.contains("\"ok\":true"))
        assertTrue(result.json.contains("\"backend\":\"offline\""))
        assertEquals(listOf("你好"), offline.spoken)
        assertTrue(fallback.spoken.isEmpty())
        assertFalse(result.json.contains("pcm"))
    }

    @Test
    fun usesSystemFallbackWhenOfflineUnready() = runTest {
        val offline = FakeTtsEngine(ready = false)
        val fallback = FakeTtsEngine(ready = true)
        val tool = SpeechOutputTool(PreferOfflineTtsPort(offline, fallback))
        val result = tool.execute("""{"text":"你好"}""", ToolContext("t", "c"))
        assertTrue(result.json.contains("\"backend\":\"system\""))
        assertTrue(offline.spoken.isEmpty())
        assertEquals(listOf("你好"), fallback.spoken)
    }

    @Test
    fun rejectsLongTextOnFallback() = runTest {
        val offline = FakeTtsEngine(ready = false)
        val fallback = FakeTtsEngine(ready = true)
        val tool = SpeechOutputTool(PreferOfflineTtsPort(offline, fallback))
        val longText = "啊".repeat(PreferOfflineTtsPort.MAX_SYSTEM_CHARS + 1)
        val result = tool.execute("""{"text":"$longText"}""", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.TTS_TOO_LONG, result.error)
        assertTrue(fallback.spoken.isEmpty())
    }

    @Test
    fun rejectsNetworkVoice() = runTest {
        val offline = FakeTtsEngine(ready = false)
        val fallback = FakeTtsEngine(ready = true, outcome = TtsOutcome.NETWORK)
        val tool = SpeechOutputTool(PreferOfflineTtsPort(offline, fallback))
        val result = tool.execute("""{"text":"你好"}""", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.TTS_NETWORK, result.error)
    }

    @Test
    fun emptyTextIsInvalid() {
        val tool = SpeechOutputTool(PreferOfflineTtsPort(FakeTtsEngine(), FakeTtsEngine()))
        try {
            tool.validateArguments("""{"text":"  "}""")
            throw AssertionError("expected AgentException")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
    }

    @Test
    fun ttsLayoutRequiresThreeFiles() {
        val dir = Files.createTempDirectory("tts-layout").toFile()
        try {
            assertFalse(TtsModelLayout.isPresent(dir))
            File(dir, TtsModelLayout.MODEL_FILE).writeText("x")
            File(dir, TtsModelLayout.TOKENS_FILE).writeText("a")
            assertFalse(TtsModelLayout.isPresent(dir))
            File(dir, TtsModelLayout.LEXICON_FILE).writeText("b")
            assertTrue(TtsModelLayout.isPresent(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sherpaTtsNeedsModelAndNative() = runTest {
        val dir = Files.createTempDirectory("sherpa-tts").toFile()
        try {
            assertFalse(
                SherpaTtsEngine(dir, nativeAvailable = { true }, speakNative = { _, _ -> TtsOutcome.SPOKEN }).isReady(),
            )
            File(dir, TtsModelLayout.MODEL_FILE).writeText("x")
            File(dir, TtsModelLayout.TOKENS_FILE).writeText("a")
            File(dir, TtsModelLayout.LEXICON_FILE).writeText("b")
            assertFalse(
                SherpaTtsEngine(dir, nativeAvailable = { false }, speakNative = { _, _ -> error("speak") }).isReady(),
            )
            var spoken = 0
            val offline = SherpaTtsEngine(
                modelDir = dir,
                nativeAvailable = { true },
                speakNative = { _, text ->
                    spoken += 1
                    assertEquals("你好", text)
                    TtsOutcome.SPOKEN
                },
            )
            val fallback = FakeTtsEngine(ready = true)
            val result = SpeechOutputTool(PreferOfflineTtsPort(offline, fallback))
                .execute("""{"text":"你好"}""", ToolContext("t", "c"))
            assertTrue(result.json.contains("\"backend\":\"offline\""))
            assertEquals(1, spoken)
            assertTrue(fallback.spoken.isEmpty())
            assertFalse(result.json.contains("pcm"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
