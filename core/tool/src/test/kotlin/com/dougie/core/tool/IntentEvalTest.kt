package com.dougie.core.tool

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentEvalTest {
    @Test
    fun fixtureHasTenPlusDistinctGoldIntentsAndOneMismatch() {
        val json = loadGold()
        val items = IntentEval.loadItems(json)
        val schema = Json.parseToJsonElement(json).jsonObject.getValue("schema").jsonArray
            .map { it.jsonPrimitive.content }
        assertTrue("schema must list >=10 intents, was ${schema.size}", schema.size >= 10)
        val golds = items.map { it.goldIntent }.toSet()
        assertTrue("need >=10 distinct gold intents, was ${golds.size}", golds.size >= 10)
        assertTrue(schema.containsAll(golds))
        val mismatches = items.count { IntentJsonParser.parse(it.modelJson).intent != it.goldIntent }
        assertTrue("fixture must include at least one wrong hypothesis", mismatches >= 1)
    }

    @Test
    fun fixtureAccuracyPassesNinetyPercent() {
        val report = IntentEval.report(IntentEval.loadItems(loadGold()))
        println(report)
        assertEquals(11, report.total)
        assertEquals(10, report.correct)
        assertEquals(10.0 / 11.0, report.accuracy, 1e-9)
        assertTrue(report.passed)
        assertFalse(IntentEval.ruleEReport(emptyList()).ruleEPassed)
    }

    @Test
    fun parseFailureCountsAsIncorrect() {
        val report = IntentEval.report(
            listOf(IntentEvalItem(id = "bad", goldIntent = "query_time", modelJson = "not-json")),
        )
        assertEquals(0, report.correct)
        assertEquals(0.0, report.accuracy, 0.0)
        assertFalse(report.passed)
    }

    @Test
    fun perfectSubsetIsOne() {
        val items = IntentEval.loadItems(loadGold()).filter { it.id != "i11" }
        val report = IntentEval.report(items)
        assertEquals(1.0, report.accuracy, 0.0)
        assertTrue(report.passed)
    }

    @Test
    fun samplePredictionsLoadOptionalFieldsAndDoNotPassRuleE() {
        val text = javaClass.getResourceAsStream("/eval/intent-predictions-sample.jsonl")!!
            .bufferedReader()
            .use { it.readText() }
        val items = IntentEval.loadJsonl(text)
        assertEquals(3, items.size)
        assertEquals("h001", items[0].id)
        assertEquals("现在几点", items[0].text)
        assertEquals("query_time", items[0].predictedIntent)
        assertEquals(12L, items[0].latencyMs)
        assertNull(items[2].predictedIntent)
        assertNull(items[2].latencyMs)
        val report = IntentEval.ruleEReport(items)
        assertEquals(3, report.nLabeled)
        assertEquals(2, report.nScored)
        assertEquals(1, report.nUnscored)
        assertEquals(3, report.nClasses)
        assertEquals(1.0, report.accuracy, 0.0)
        assertEquals(12L, report.p95Ms)
        assertTrue(report.latencyApplied)
        assertFalse(report.ruleEPassed)
        val dump = report.toString()
        assertFalse(dump.contains("现在几点"))
        assertFalse(dump.contains("还剩多少电"))
        assertFalse(dump.contains("帮我查一下日程"))
        assertFalse(dump.contains("query_time"))
        assertFalse(dump.contains("query_battery"))
        assertFalse(dump.contains("query_calendar"))
    }

    @Test
    fun jsonNullPredictedAndLatencyAreUnscored() {
        val items = IntentEval.loadJsonl(
            """{"id":"n1","text":"x","goldIntent":"query_time","predictedIntent":null,"latencyMs":null}""",
        )
        assertEquals(1, items.size)
        assertNull(items[0].predictedIntent)
        assertNull(items[0].latencyMs)
        val report = IntentEval.ruleEReport(items)
        assertEquals(0, report.nScored)
        assertEquals(1, report.nUnscored)
        assertEquals(0.0, report.accuracy, 0.0)
        assertFalse(report.latencyApplied)
        assertFalse(report.ruleEPassed)
    }

    @Test
    fun emptyListDoesNotPass() {
        val report = IntentEval.ruleEReport(emptyList())
        assertEquals(0, report.nLabeled)
        assertEquals(0, report.nScored)
        assertEquals(0, report.nClasses)
        assertEquals(0.0, report.accuracy, 0.0)
        assertEquals(0L, report.p95Ms)
        assertFalse(report.latencyApplied)
        assertFalse(report.ruleEPassed)
    }

    @Test
    fun fewerThanEightyEightLabeledCannotPass() {
        val report = IntentEval.ruleEReport(perfect(87))
        assertEquals(87, report.nLabeled)
        assertEquals(87, report.nScored)
        assertEquals(11, report.nClasses)
        assertEquals(1.0, report.accuracy, 0.0)
        assertTrue(report.latencyApplied)
        assertTrue(report.p95Ms <= IntentEval.P95_LIMIT_MS)
        assertFalse(report.ruleEPassed)
    }

    @Test
    fun fewerThanEightyEightScoredCannotPass() {
        val items = perfect(88).mapIndexed { i, item ->
            if (i == 0) item.copy(predictedIntent = null, latencyMs = null) else item
        }
        val report = IntentEval.ruleEReport(items)
        assertEquals(88, report.nLabeled)
        assertEquals(87, report.nScored)
        assertEquals(1, report.nUnscored)
        assertEquals(1.0, report.accuracy, 0.0)
        assertTrue(report.latencyApplied)
        assertFalse(report.ruleEPassed)
    }

    @Test
    fun eightyEightPerfectItemsPassRuleE() {
        val report = IntentEval.ruleEReport(perfect(88))
        assertEquals(88, report.nLabeled)
        assertEquals(88, report.nScored)
        assertEquals(0, report.nUnscored)
        assertEquals(11, report.nClasses)
        assertEquals(1.0, report.accuracy, 0.0)
        assertEquals(10L, report.p95Ms)
        assertTrue(report.latencyApplied)
        assertTrue(report.ruleEPassed)
    }

    @Test
    fun accuracyFlipsAtNinetyPercent() {
        val pass = IntentEval.ruleEReport(withMismatches(mismatchCount = 8))
        assertEquals(80.0 / 88.0, pass.accuracy, 1e-9)
        assertTrue(pass.latencyApplied)
        assertTrue(pass.ruleEPassed)

        val fail = IntentEval.ruleEReport(withMismatches(mismatchCount = 9))
        assertEquals(79.0 / 88.0, fail.accuracy, 1e-9)
        assertTrue(fail.latencyApplied)
        assertFalse(fail.ruleEPassed)
    }

    @Test
    fun p95FlipsAtFiveHundredMs() {
        val pass = IntentEval.ruleEReport(withLatencies(slowCount = 4, slowMs = 501L))
        assertEquals(500L, pass.p95Ms)
        assertTrue(pass.latencyApplied)
        assertTrue(pass.ruleEPassed)

        val fail = IntentEval.ruleEReport(withLatencies(slowCount = 5, slowMs = 501L))
        assertEquals(501L, fail.p95Ms)
        assertTrue(fail.latencyApplied)
        assertFalse(fail.ruleEPassed)
    }

    @Test
    fun predictionsWithoutLatencyCannotPass() {
        val items = perfect(88).map { it.copy(latencyMs = null) }
        val report = IntentEval.ruleEReport(items)
        assertEquals(88, report.nScored)
        assertEquals(1.0, report.accuracy, 0.0)
        assertEquals(0L, report.p95Ms)
        assertFalse(report.latencyApplied)
        assertFalse(report.ruleEPassed)
    }

    @Test
    fun mixedMissingLatencyCannotPass() {
        val items = perfect(88).mapIndexed { i, item ->
            if (i == 0) item.copy(latencyMs = null) else item
        }
        val report = IntentEval.ruleEReport(items)
        assertEquals(88, report.nScored)
        assertEquals(1.0, report.accuracy, 0.0)
        assertFalse(report.latencyApplied)
        assertFalse(report.ruleEPassed)
    }

    @Test
    fun fewerThanTenClassesCannotPass() {
        val nine = GOLD_LABELS.take(9)
        val fail = IntentEval.ruleEReport(perfect(88, labels = nine))
        assertEquals(9, fail.nClasses)
        assertEquals(88, fail.nLabeled)
        assertEquals(88, fail.nScored)
        assertEquals(1.0, fail.accuracy, 0.0)
        assertTrue(fail.latencyApplied)
        assertFalse(fail.ruleEPassed)

        val ten = IntentEval.ruleEReport(perfect(88, labels = GOLD_LABELS.take(10)))
        assertEquals(10, ten.nClasses)
        assertTrue(ten.ruleEPassed)
    }

    @Test
    fun timedClassifyReturnsFakeIntentAndNonNegativeMs() = runTest {
        val engine = FakeIntentEngine(
            hit = IntentHit(intent = "query_battery", route = "battery", confidence = 0.9),
        )
        val (intent, ms) = IntentEval.timedClassify(engine, "还剩多少电")
        assertEquals("query_battery", intent)
        assertTrue(ms >= 0L)
        assertEquals(listOf("还剩多少电"), engine.classified)
    }

    private fun loadGold(): String =
        javaClass.getResourceAsStream("/eval/intent-gold.json")!!.bufferedReader().use { it.readText() }

    private fun perfect(n: Int, labels: List<String> = GOLD_LABELS): List<IntentPredItem> =
        List(n) { i ->
            val gold = labels[i % labels.size]
            IntentPredItem(
                id = "h$i",
                text = "utterance-$i",
                goldIntent = gold,
                predictedIntent = gold,
                latencyMs = 10L,
            )
        }

    /** [mismatchCount] scored rows predict a different gold label; the rest match. */
    private fun withMismatches(mismatchCount: Int): List<IntentPredItem> =
        perfect(88).mapIndexed { i, item ->
            if (i < mismatchCount) {
                val wrong = GOLD_LABELS[(GOLD_LABELS.indexOf(item.goldIntent) + 1) % GOLD_LABELS.size]
                item.copy(predictedIntent = wrong)
            } else {
                item
            }
        }

    /**
     * 88 items: the last [slowCount] have [slowMs]; the rest are 500ms.
     * nearest-rank index for n=88 is ceil(0.95*88)-1 = 83, so slowCount=4 still p95=500 and slowCount=5 is 501.
     */
    private fun withLatencies(slowCount: Int, slowMs: Long): List<IntentPredItem> =
        perfect(88).mapIndexed { i, item ->
            val slow = i >= 88 - slowCount
            item.copy(latencyMs = if (slow) slowMs else IntentEval.P95_LIMIT_MS)
        }

    companion object {
        private val GOLD_LABELS = listOf(
            "query_time",
            "query_battery",
            "query_calendar",
            "create_calendar",
            "query_location",
            "clipboard_read",
            "clipboard_write",
            "open_app",
            "screen_capture",
            "speech_input",
            "unknown",
        )
    }
}
