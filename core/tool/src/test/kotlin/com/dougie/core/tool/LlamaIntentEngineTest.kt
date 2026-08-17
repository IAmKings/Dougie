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

class LlamaIntentEngineTest {
    @Test
    fun parserReadsWrappedJson() {
        val hit = IntentJsonParser.parse(
            """here {"intent":"query_time","slots":{"when":"now"},"route":"time","confidence":0.91} thanks""",
        )
        assertEquals("query_time", hit.intent)
        assertEquals("time", hit.route)
        assertEquals("now", hit.slots["when"])
        assertEquals(0.91, hit.confidence, 0.0)
    }

    @Test
    fun parserRejectsNonJson() {
        try {
            IntentJsonParser.parse("not json")
            throw AssertionError("expected AgentException")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INTENT_FAILED, e.userMessage)
        }
    }

    @Test
    fun parserSkipsInvalidBraceGroupThenReadsJson() {
        val hit = IntentJsonParser.parse(
            """note {not json} then {"intent":"query_time","slots":{},"route":"time","confidence":0.8}""",
        )
        assertEquals("query_time", hit.intent)
        assertEquals("time", hit.route)
        assertEquals(0.8, hit.confidence, 0.0)
    }

    @Test
    fun llamaEngineNeedsModelAndNative() = runTest {
        val dir = Files.createTempDirectory("llama-engine").toFile()
        try {
            var calls = 0
            val complete: (File, String) -> String = { _, _ ->
                calls += 1
                error("complete")
            }
            val missingModel = LlamaIntentEngine(dir, nativeAvailable = { true }, complete = complete)
            assertFalse(missingModel.isReady())
            try {
                missingModel.classify("现在几点")
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.INTENT_ENGINE_NOT_READY, e.userMessage)
            }
            assertEquals(0, calls)
            File(dir, IntentModelLayout.MODEL_FILE).writeText("x")
            val missingNative = LlamaIntentEngine(dir, nativeAvailable = { false }, complete = complete)
            assertFalse(missingNative.isReady())
            try {
                missingNative.classify("现在几点")
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.INTENT_ENGINE_NOT_READY, e.userMessage)
            }
            assertEquals(0, calls)
            val engine = LlamaIntentEngine(
                modelDir = dir,
                nativeAvailable = { true },
                complete = { _, prompt ->
                    calls += 1
                    assertTrue(prompt.contains("现在几点"))
                    assertFalse(prompt.contains("gguf"))
                    """{"intent":"query_time","slots":{},"route":"time","confidence":0.88}"""
                },
            )
            val port = object : IntentPort {
                override fun isModelPresent(): Boolean = true
                override fun isEngineReady(): Boolean = engine.isReady()
                override suspend fun classify(text: String) = engine.classify(text)
            }
            val result = IntentClassifierTool(port).execute("""{"text":"现在几点"}""", ToolContext("t", "c"))
            assertTrue(result.json.contains("\"intent\":\"query_time\""))
            assertEquals(1, calls)
            assertFalse(result.json.contains("gguf"))
            assertFalse(result.json.contains("prompt"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
