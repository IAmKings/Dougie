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

class OnnxIntentEngineTest {
    @Test
    fun engineNeedsModelAndNative() = runTest {
        val dir = Files.createTempDirectory("onnx-engine").toFile()
        try {
            var calls = 0
            val infer: (File, FloatArray) -> FloatArray = { _, _ ->
                calls += 1
                error("infer")
            }
            val missingModel = OnnxIntentEngine(dir, nativeAvailable = { true }, infer = infer)
            assertFalse(missingModel.isReady())
            try {
                missingModel.classify("现在几点")
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.INTENT_MODEL_MISSING, e.userMessage)
            }
            assertEquals(0, calls)
            copyFixture(dir)
            val missingNative = OnnxIntentEngine(dir, nativeAvailable = { false }, infer = infer)
            assertFalse(missingNative.isReady())
            try {
                missingNative.classify("现在几点")
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.INTENT_ENGINE_NOT_READY, e.userMessage)
            }
            assertEquals(0, calls)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun nowQueryTimeHasHighConfidence() = runTest {
        val dir = Files.createTempDirectory("onnx-engine-hit").toFile()
        try {
            copyFixture(dir)
            val spec = IntentHashBagFeaturizer.loadSpec(File(dir, IntentModelLayout.TOKENIZER_FILE))
            val prototype = IntentHashBagFeaturizer.featurize("现在几点", spec)
            val used = prototype.mapIndexed { index, value -> index to value }.filter { it.second != 0f }.map { it.first }
            assertEquals(listOf(20, 31, 36, 37, 43, 47, 56), used)
            var calls = 0
            val engine = OnnxIntentEngine(
                modelDir = dir,
                nativeAvailable = { true },
                infer = { _, features ->
                    calls += 1
                    FloatArray(11) { index ->
                        if (index == 0) {
                            var dot = 0f
                            for (i in features.indices) {
                                dot += features[i] * prototype[i]
                            }
                            dot
                        } else {
                            0f
                        }
                    }
                },
            )
            val port = object : IntentPort {
                override fun isModelPresent(): Boolean = true
                override fun isEngineReady(): Boolean = engine.isReady()
                override suspend fun classify(text: String) = engine.classify(text)
            }
            val result = IntentClassifierTool(port).execute("""{"text":"现在几点"}""", ToolContext("t", "c"))
            assertTrue(result.json.contains("\"intent\":\"query_time\""))
            assertTrue(result.json.contains("\"route\":\"time\""))
            assertEquals(1, calls)
            val hit = engine.classify("现在几点")
            assertEquals("query_time", hit.intent)
            assertTrue(hit.confidence >= IntentModelLayout.MIN_CONFIDENCE)
            assertFalse(result.json.contains("gguf"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun blankInferFails() = runTest {
        val dir = Files.createTempDirectory("onnx-engine-blank").toFile()
        try {
            copyFixture(dir)
            val engine = OnnxIntentEngine(dir, nativeAvailable = { true }, infer = { _, _ -> floatArrayOf() })
            try {
                engine.classify("现在几点")
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.INTENT_FAILED, e.userMessage)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun lowConfidenceStillReturnsHit() = runTest {
        val dir = Files.createTempDirectory("onnx-engine-low").toFile()
        try {
            copyFixture(dir)
            val engine = OnnxIntentEngine(
                dir,
                nativeAvailable = { true },
                infer = { _, _ -> FloatArray(11) { 0f } },
            )
            val hit = engine.classify("现在几点")
            assertEquals("query_time", hit.intent)
            assertTrue(hit.confidence < IntentModelLayout.MIN_CONFIDENCE)
            assertTrue(hit.confidence > 0.0)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun bertPackUsesTokenInfer() = runTest {
        val dir = Files.createTempDirectory("onnx-bert").toFile()
        try {
            File(dir, IntentModelLayout.MODEL_FILE).writeText("x")
            File(dir, IntentModelLayout.TOKENIZER_FILE).writeText(
                """{"algorithm":"bert_wordpiece","max_len":8}""",
            )
            File(dir, IntentModelLayout.VOCAB_FILE).writeText(
                listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "现", "在", "几", "点").joinToString("\n"),
            )
            File(dir, IntentModelLayout.LABELS_FILE).writeText(
                listOf(
                    "query_time", "query_battery", "query_calendar", "create_calendar",
                    "query_location", "clipboard_read", "clipboard_write", "open_app",
                    "screen_capture", "speech_input", "unknown",
                ).joinToString("\n"),
            )
            var tokenCalls = 0
            val engine = OnnxIntentEngine(
                dir,
                nativeAvailable = { true },
                infer = { _, _ -> error("hashbag") },
                inferTokens = { _, ids, mask ->
                    tokenCalls += 1
                    assertEquals(8, ids.size)
                    assertEquals(8, mask.size)
                    FloatArray(11) { if (it == 0) 4f else 0f }
                },
            )
            assertTrue(engine.isReady())
            val hit = engine.classify("现在几点")
            assertEquals(1, tokenCalls)
            assertEquals("query_time", hit.intent)
            assertTrue(hit.confidence >= IntentModelLayout.MIN_CONFIDENCE)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ggufAloneIsNotPresent() {
        val dir = Files.createTempDirectory("onnx-gguf").toFile()
        try {
            File(dir, "model.gguf").writeText("x")
            File(dir, "quant.id").writeText("intent-q8")
            assertFalse(IntentModelLayout.isPresent(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun copyFixture(dir: File) {
        listOf(
            IntentModelLayout.MODEL_FILE,
            IntentModelLayout.TOKENIZER_FILE,
            IntentModelLayout.LABELS_FILE,
        ).forEach { name ->
            val stream = javaClass.getResourceAsStream("/intent-pack/$name")
                ?: error("missing fixture $name")
            stream.use { input -> File(dir, name).outputStream().use { output -> input.copyTo(output) } }
        }
    }
}
