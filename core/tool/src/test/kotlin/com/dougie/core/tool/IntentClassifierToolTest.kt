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

class IntentClassifierToolTest {
    @Test
    fun missingModelDoesNotClassify() = runTest {
        val port = FakeIntentPort(modelPresent = false)
        val result = IntentClassifierTool(port).execute("""{"text":"现在几点"}""", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.INTENT_MODEL_MISSING, result.error)
        assertEquals(0, port.classifyCount)
    }

    @Test
    fun missingEngineDoesNotClassify() = runTest {
        val port = FakeIntentPort(engineReady = false)
        val result = IntentClassifierTool(port).execute("""{"text":"现在几点"}""", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.INTENT_ENGINE_NOT_READY, result.error)
        assertEquals(0, port.classifyCount)
    }

    @Test
    fun lowConfidenceFails() = runTest {
        val port = FakeIntentPort(
            hit = IntentHit(intent = "unknown", slots = emptyMap(), route = "clarify", confidence = 0.2),
        )
        val result = IntentClassifierTool(port).execute("""{"text":"嗯"}""", ToolContext("t", "c"))
        assertEquals(UserFacingErrors.INTENT_LOW_CONFIDENCE, result.error)
        assertEquals(1, port.classifyCount)
    }

    @Test
    fun successJsonHasIntentNotWeights() = runTest {
        val port = FakeIntentPort(
            hit = IntentHit(
                intent = "query_time",
                slots = mapOf("when" to "now"),
                route = "time",
                confidence = 0.91,
            ),
        )
        val result = IntentClassifierTool(port).execute("""{"text":"现在几点"}""", ToolContext("t", "c"))
        assertTrue(result.json.contains("\"ok\":true"))
        assertTrue(result.json.contains("\"intent\":\"query_time\""))
        assertTrue(result.json.contains("\"route\":\"time\""))
        assertFalse(result.json.contains("gguf"))
        assertFalse(result.json.contains("prompt"))
        assertEquals(1, port.classifyCount)
    }

    @Test
    fun emptyTextIsInvalid() {
        try {
            IntentClassifierTool(FakeIntentPort()).validateArguments("""{"text":"  "}""")
            throw AssertionError("expected AgentException")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
    }

    @Test
    fun layoutRequiresGguf() {
        val dir = Files.createTempDirectory("intent-layout").toFile()
        try {
            assertFalse(IntentModelLayout.isPresent(dir))
            File(dir, IntentModelLayout.MODEL_FILE).writeText("x")
            assertTrue(IntentModelLayout.isPresent(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
