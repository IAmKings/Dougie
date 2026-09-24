package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.ToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalToolContractEvalTest {
    @Test
    fun frozenSetHasFourteenRowsAndProtocolGate() {
        val items = LocalToolContractEval.loadResource()
        assertEquals(LocalToolContractEval.EXPECTED_N, items.size)
        val counts = items.groupingBy { it.expectTool }.eachCount()
        LocalToolContractEval.ALLOWED_TOOLS.forEach { name ->
            assertEquals(2, counts[name])
        }
        assertEquals(2, counts[null])
        assertTrue(LocalToolContractEval.protocolActiveFor(items))
        val descriptors = LocalToolContractEval.ALLOWED_TOOLS.map {
            ToolDescriptor(it, description = "contract")
        }
        listOf("你好", "你是谁").forEach { input ->
            assertFalse(
                ChatPromptAssembler.localToolProtocolActive(
                    AgentTask(taskId = "idle", input = input),
                    descriptors,
                ),
            )
        }
    }

    @Test
    fun cannedAllCorrectPassesAndOneMissFails() {
        val items = LocalToolContractEval.loadResource()
        val perfect = items.associate { it.id to it.expectTool }
        val pass = LocalToolContractEval.score(items, perfect)
        assertTrue(pass.passed)
        assertEquals(14, pass.correct)
        assertFalse(pass.toString().contains("现在几点"))
        assertFalse(pass.toString().contains("time"))
        assertEquals("local-tool n=14 correct=14 passed=true", pass.toString())
        val missed = perfect.toMutableMap()
        missed.remove("time-1")
        val fail = LocalToolContractEval.score(items, missed)
        assertFalse(fail.passed)
        assertEquals(13, fail.correct)
        val blank = perfect.toMutableMap()
        blank["chat-1"] = ""
        assertTrue(LocalToolContractEval.score(items, blank).passed)
    }

    @Test
    fun unknownToolAndDuplicateIdFailLoad() {
        assertThrows { LocalToolContractEval.load("""{"id":"a","text":"现在几点","expectTool":"sms"}""") }
        assertThrows {
            LocalToolContractEval.load(
                """
                {"id":"a","text":"现在几点","expectTool":"time"}
                {"id":"a","text":"电量还有多少","expectTool":"battery"}
                """.trimIndent(),
            )
        }
    }

    @Test
    fun predictionsRoundTripOmitsUtterance() {
        val dir = kotlin.io.path.createTempDirectory("contract").toFile()
        try {
            val file = File(dir, "predictions.jsonl")
            LocalToolContractEval.writePredictions(file, linkedMapOf("time-1" to "time", "chat-1" to null))
            val text = file.readText()
            assertFalse(text.contains("现在几点"))
            val loaded = LocalToolContractEval.loadPredictions(text)
            assertEquals("time", loaded["time-1"])
            assertEquals(null, loaded["chat-1"])
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalStateException) {
            return
        }
        throw AssertionError("expected load failure")
    }
}
