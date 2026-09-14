package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrEvalTest {
    @Test
    fun sampleManifestLoadsOptionalFieldsAndDoesNotPassRuleD() {
        val text = javaClass.getResourceAsStream("/eval/asr-manifest-sample.jsonl")!!
            .bufferedReader()
            .use { it.readText() }
        val items = AsrEval.loadJsonl(text)
        assertEquals(3, items.size)
        assertEquals("d001", items[0].id)
        assertEquals("wav/d001.wav", items[0].wav)
        assertEquals("现在几点", items[0].hypothesis)
        assertEquals(true, items[0].vadOk)
        assertNull(items[2].wav)
        assertNull(items[2].hypothesis)
        assertNull(items[2].vadOk)
        val report = AsrEval.report(items)
        assertEquals(3, report.nLabeled)
        assertEquals(2, report.nScored)
        assertEquals(1, report.nUnscored)
        assertEquals(0.0, report.meanCer, 0.0)
        assertEquals(1.0, report.successRate, 0.0)
        assertTrue(report.vadApplied)
        assertFalse(report.ruleDPassed)
        assertFalse(report.toString().contains("现在几点"))
        assertFalse(report.toString().contains("帮我查一下电量"))
    }

    @Test
    fun fewerThanFiveHundredLabeledCannotPass() {
        val items = List(499) { i ->
            AsrEvalItem(
                id = "n$i",
                reference = "今天天气很好",
                hypothesis = "今天天气很好",
                vadOk = true,
            )
        }
        val report = AsrEval.report(items)
        assertEquals(499, report.nLabeled)
        assertEquals(499, report.nScored)
        assertEquals(0.0, report.meanCer, 0.0)
        assertEquals(1.0, report.successRate, 0.0)
        assertTrue(report.vadApplied)
        assertFalse(report.ruleDPassed)
    }

    @Test
    fun fiveHundredPerfectItemsPassRuleD() {
        val report = AsrEval.report(perfect(500))
        assertEquals(500, report.nLabeled)
        assertEquals(500, report.nScored)
        assertEquals(0, report.nUnscored)
        assertEquals(0.0, report.meanCer, 0.0)
        assertEquals(1.0, report.successRate, 0.0)
        assertTrue(report.vadApplied)
        assertTrue(report.ruleDPassed)
    }

    @Test
    fun meanCerAboveLimitFailsRuleD() {
        val items = List(500) { i ->
            AsrEvalItem(
                id = "c$i",
                reference = "今天天气很好",
                hypothesis = "今天天气很差",
                vadOk = true,
            )
        }
        val report = AsrEval.report(items)
        assertTrue(report.meanCer > AsrEval.CER_LIMIT)
        assertEquals(0.0, report.successRate, 0.0)
        assertTrue(report.vadApplied)
        assertFalse(report.ruleDPassed)
    }

    @Test
    fun successRateFlipsAtNinetyFivePercent() {
        val pass = AsrEval.report(withCerFailures(failCount = 25))
        assertEquals(0.95, pass.successRate, 1e-9)
        assertTrue(pass.meanCer <= AsrEval.CER_LIMIT)
        assertTrue(pass.vadApplied)
        assertTrue(pass.ruleDPassed)

        val fail = AsrEval.report(withCerFailures(failCount = 26))
        assertEquals(474.0 / 500.0, fail.successRate, 1e-9)
        assertTrue(fail.vadApplied)
        assertFalse(fail.ruleDPassed)
    }

    @Test
    fun vadFalseIsNotSuccessWhenVadApplied() {
        val items = List(500) { i ->
            AsrEvalItem(
                id = "f$i",
                reference = "打开日历",
                hypothesis = "打开日历",
                vadOk = i >= 26,
            )
        }
        val report = AsrEval.report(items)
        assertTrue(report.vadApplied)
        assertEquals(474.0 / 500.0, report.successRate, 1e-9)
        assertEquals(0.0, report.meanCer, 0.0)
        assertFalse(report.ruleDPassed)
    }

    @Test
    fun hypothesisWithoutVadCannotPass() {
        val items = List(500) { i ->
            AsrEvalItem(
                id = "v$i",
                reference = "打开日历",
                hypothesis = "打开日历",
                vadOk = null,
            )
        }
        val report = AsrEval.report(items)
        assertEquals(500, report.nScored)
        assertEquals(0.0, report.meanCer, 0.0)
        assertEquals(1.0, report.successRate, 0.0)
        assertFalse(report.vadApplied)
        assertFalse(report.ruleDPassed)
    }

    @Test
    fun mixedMissingVadCannotPass() {
        val items = List(500) { i ->
            AsrEvalItem(
                id = "m$i",
                reference = "打开日历",
                hypothesis = "打开日历",
                vadOk = if (i == 0) null else true,
            )
        }
        val report = AsrEval.report(items)
        assertEquals(500, report.nScored)
        assertEquals(0.0, report.meanCer, 0.0)
        assertEquals(1.0, report.successRate, 0.0)
        assertFalse(report.vadApplied)
        assertFalse(report.ruleDPassed)
    }

    @Test
    fun fiveHundredLabeledButFewerThanFiveHundredScoredCannotPass() {
        val items = List(500) { i ->
            AsrEvalItem(
                id = "p$i",
                reference = "打开日历",
                hypothesis = if (i == 0) null else "打开日历",
                vadOk = if (i == 0) null else true,
            )
        }
        val report = AsrEval.report(items)
        assertEquals(500, report.nLabeled)
        assertEquals(499, report.nScored)
        assertEquals(1, report.nUnscored)
        assertEquals(0.0, report.meanCer, 0.0)
        assertTrue(report.vadApplied)
        assertFalse(report.ruleDPassed)
    }

    @Test
    fun missingHypothesisIsUnscoredAndCannotPass() {
        val items = List(500) { i ->
            AsrEvalItem(id = "u$i", reference = "打开日历", wav = "wav/u$i.wav")
        }
        val report = AsrEval.report(items)
        assertEquals(500, report.nLabeled)
        assertEquals(0, report.nScored)
        assertEquals(500, report.nUnscored)
        assertEquals(0.0, report.meanCer, 0.0)
        assertEquals(0.0, report.successRate, 0.0)
        assertFalse(report.vadApplied)
        assertFalse(report.ruleDPassed)
    }

    @Test
    fun emptyListDoesNotPass() {
        val report = AsrEval.report(emptyList())
        assertEquals(0, report.nLabeled)
        assertFalse(report.vadApplied)
        assertFalse(report.ruleDPassed)
    }

    private fun perfect(n: Int): List<AsrEvalItem> =
        List(n) { i ->
            AsrEvalItem(
                id = "p$i",
                reference = "今天天气很好",
                hypothesis = "今天天气很好",
                vadOk = true,
            )
        }

    /**
     * 500 items: [failCount] have CER > 5% (one substitution in a 4-char ref), the rest exact.
     * Mean CER stays ≤ 5% at failCount=25 (1/4 * 25/500 = 0.0125).
     */
    private fun withCerFailures(failCount: Int): List<AsrEvalItem> =
        List(500) { i ->
            val fail = i < failCount
            AsrEvalItem(
                id = "s$i",
                reference = "abcd",
                hypothesis = if (fail) "abxd" else "abcd",
                vadOk = true,
            )
        }
}
