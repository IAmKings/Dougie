package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
