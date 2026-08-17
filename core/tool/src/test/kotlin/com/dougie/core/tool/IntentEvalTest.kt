package com.dougie.core.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun loadGold(): String =
        javaClass.getResourceAsStream("/eval/intent-gold.json")!!.bufferedReader().use { it.readText() }
}
