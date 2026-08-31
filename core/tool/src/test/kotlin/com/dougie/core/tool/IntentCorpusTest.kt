package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class IntentCorpusTest {
    @Test
    fun heldoutHasAtLeastEightPerLabelAndNoTrainOverlap() {
        val train = load("train.jsonl")
        val held = load("heldout.jsonl")
        val labels = javaClass.getResourceAsStream("/intent-pack/labels.txt")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }
        for (label in labels) {
            assertTrue(label, train.count { it.second == label } >= 30)
            assertTrue(label, held.count { it.second == label } >= 8)
        }
        val overlap = train.map { it.first }.toSet().intersect(held.map { it.first }.toSet())
        assertEquals(emptySet<String>(), overlap)
    }

    private fun load(name: String): List<Pair<String, String>> {
        val raw = javaClass.getResourceAsStream("/intent-corpus/$name")!!.bufferedReader().readText()
        return raw.lineSequence().filter { it.isNotBlank() }.map { line ->
            val obj = Json.parseToJsonElement(line).jsonObject
            obj.getValue("text").jsonPrimitive.content to obj.getValue("intent").jsonPrimitive.content
        }.toList()
    }
}
