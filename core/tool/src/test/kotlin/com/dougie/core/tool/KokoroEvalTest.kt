package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroEvalTest {
    @Test
    fun sampleFixtureLoadsOptionalFieldsAndDoesNotPassRuleB() {
        assertEquals("eval/tts/kokoro-rtf.jsonl", KokoroEval.MANIFEST_RELATIVE)
        val text = javaClass.getResourceAsStream("/eval/kokoro-rtf-sample.jsonl")!!
            .bufferedReader()
            .use { it.readText() }
        val items = KokoroEval.loadJsonl(text)
        assertEquals(3, items.size)
        assertEquals("k001", items[0].id)
        assertEquals("现在几点了", items[0].text)
        assertEquals(820L, items[0].synthMs)
        assertEquals(1000L, items[0].audioDurationMs)
        assertEquals(1, items[0].numThreads)
        assertEquals(true, items[0].naturalnessOk)
        assertNull(items[2].synthMs)
        assertNull(items[2].audioDurationMs)
        assertNull(items[2].numThreads)
        assertNull(items[2].naturalnessOk)
        val report = KokoroEval.report(items)
        assertEquals(3, report.nLabeled)
        assertEquals(2, report.nScored)
        assertEquals(1, report.nUnscored)
        assertEquals(0.82, report.p95Rtf, 1e-9)
        assertTrue(report.threadsApplied)
        assertTrue(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
        val dump = report.toString()
        assertFalse(dump.contains("现在几点了"))
        assertFalse(dump.contains("今天天气很好"))
        assertFalse(dump.contains("帮我查一下电量"))
    }

    @Test
    fun jsonNullOptionalFieldsAreMissingAndUnscored() {
        val items = KokoroEval.loadJsonl(
            """{"id":"n1","text":"x","synthMs":null,"audioDurationMs":null,"numThreads":null,"naturalnessOk":null}""",
        )
        assertEquals(1, items.size)
        assertNull(items[0].synthMs)
        assertNull(items[0].audioDurationMs)
        assertNull(items[0].numThreads)
        assertNull(items[0].naturalnessOk)
        val report = KokoroEval.report(items)
        assertEquals(0, report.nScored)
        assertEquals(1, report.nUnscored)
        assertEquals(0.0, report.p95Rtf, 0.0)
        assertFalse(report.threadsApplied)
        assertFalse(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
    }

    @Test
    fun emptyListDoesNotPass() {
        val report = KokoroEval.report(emptyList())
        assertEquals(0, report.nLabeled)
        assertEquals(0, report.nScored)
        assertEquals(0.0, report.p95Rtf, 0.0)
        assertFalse(report.threadsApplied)
        assertFalse(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
    }

    @Test
    fun fewerThanFiveLabeledCannotPass() {
        val report = KokoroEval.report(perfect(4))
        assertEquals(4, report.nLabeled)
        assertEquals(4, report.nScored)
        assertEquals(0.5, report.p95Rtf, 1e-9)
        assertTrue(report.threadsApplied)
        assertTrue(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
    }

    @Test
    fun fewerThanFiveScoredCannotPass() {
        val items = perfect(5).mapIndexed { i, item ->
            if (i == 0) item.copy(synthMs = null, audioDurationMs = null) else item
        }
        val report = KokoroEval.report(items)
        assertEquals(5, report.nLabeled)
        assertEquals(4, report.nScored)
        assertEquals(1, report.nUnscored)
        assertTrue(report.threadsApplied)
        assertTrue(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
    }

    @Test
    fun nonPositiveDurationOrNegativeSynthIsUnscored() {
        val items = listOf(
            perfect(1).single().copy(id = "z", audioDurationMs = 0),
            perfect(1).single().copy(id = "n", audioDurationMs = -1),
            perfect(1).single().copy(id = "s", synthMs = -1),
        )
        val report = KokoroEval.report(items)
        assertEquals(3, report.nLabeled)
        assertEquals(0, report.nScored)
        assertEquals(3, report.nUnscored)
        assertFalse(report.ruleBPassed)
        assertEquals(0.0, KokoroEval.rtf(820, 0), 0.0)
        assertEquals(0.0, KokoroEval.rtf(820, -1), 0.0)
        assertEquals(0.82, KokoroEval.rtf(820, 1000), 1e-9)
    }

    @Test
    fun fivePerfectItemsPassRuleB() {
        val report = KokoroEval.report(perfect(5))
        assertEquals(5, report.nLabeled)
        assertEquals(5, report.nScored)
        assertEquals(0, report.nUnscored)
        assertEquals(0.5, report.p95Rtf, 1e-9)
        assertTrue(report.threadsApplied)
        assertTrue(report.naturalnessApplied)
        assertTrue(report.ruleBPassed)
        assertFalse(report.toString().contains("utterance-"))
    }

    @Test
    fun p95RtfFlipsAboveOne() {
        val pass = KokoroEval.report(withSlowSynthMs(slowSynthMs = 1000L))
        assertEquals(1.0, pass.p95Rtf, 1e-9)
        assertTrue(pass.threadsApplied)
        assertTrue(pass.naturalnessApplied)
        assertTrue(pass.ruleBPassed)

        val fail = KokoroEval.report(withSlowSynthMs(slowSynthMs = 1001L))
        assertEquals(1.001, fail.p95Rtf, 1e-9)
        assertTrue(fail.threadsApplied)
        assertTrue(fail.naturalnessApplied)
        assertFalse(fail.ruleBPassed)
    }

    @Test
    fun numThreadsMissingCannotPass() {
        val items = perfect(5).mapIndexed { i, item ->
            if (i == 0) item.copy(numThreads = null) else item
        }
        val report = KokoroEval.report(items)
        assertEquals(5, report.nScored)
        assertEquals(0.5, report.p95Rtf, 1e-9)
        assertFalse(report.threadsApplied)
        assertTrue(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
    }

    @Test
    fun numThreadsNotOneCannotPass() {
        val items = perfect(5).mapIndexed { i, item ->
            if (i == 0) item.copy(numThreads = 2) else item
        }
        val report = KokoroEval.report(items)
        assertEquals(5, report.nScored)
        assertFalse(report.threadsApplied)
        assertTrue(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
    }

    @Test
    fun naturalnessMissingCannotPass() {
        val items = perfect(5).mapIndexed { i, item ->
            if (i == 0) item.copy(naturalnessOk = null) else item
        }
        val report = KokoroEval.report(items)
        assertEquals(5, report.nScored)
        assertTrue(report.threadsApplied)
        assertFalse(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
    }

    @Test
    fun naturalnessFalseCannotPass() {
        val items = perfect(5).mapIndexed { i, item ->
            if (i == 0) item.copy(naturalnessOk = false) else item
        }
        val report = KokoroEval.report(items)
        assertEquals(5, report.nScored)
        assertTrue(report.threadsApplied)
        assertFalse(report.naturalnessApplied)
        assertFalse(report.ruleBPassed)
    }

    private fun perfect(n: Int): List<KokoroEvalItem> =
        List(n) { i ->
            KokoroEvalItem(
                id = "k$i",
                text = "utterance-$i",
                synthMs = 500L,
                audioDurationMs = 1000L,
                numThreads = 1,
                naturalnessOk = true,
            )
        }

    /**
     * 5 items: four at RTF 0.5, last at [slowSynthMs]/1000.
     * nearest-rank index for n=5 is ceil(0.95*5)-1 = 4, so the slowest value is p95.
     */
    private fun withSlowSynthMs(slowSynthMs: Long): List<KokoroEvalItem> =
        perfect(5).mapIndexed { i, item ->
            if (i == 4) item.copy(synthMs = slowSynthMs) else item
        }
}
