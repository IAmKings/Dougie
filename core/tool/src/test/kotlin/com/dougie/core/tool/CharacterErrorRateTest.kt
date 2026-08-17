package com.dougie.core.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterErrorRateTest {
    @Test
    fun exactMatchIsZero() {
        assertEquals(0.0, CharacterErrorRate.cer("今天天气很好", "今天天气很好"), 0.0)
        assertEquals(0.0, CharacterErrorRate.cer(" 今 天 ", "今天"), 0.0)
    }

    @Test
    fun substitutionInsertDeleteArePositive() {
        assertEquals(1.0 / 4.0, CharacterErrorRate.cer("abcd", "abxd"), 1e-9)
        assertEquals(1.0 / 4.0, CharacterErrorRate.cer("abxcd", "abcd"), 1e-9)
        assertEquals(1.0 / 4.0, CharacterErrorRate.cer("abd", "abcd"), 1e-9)
    }

    @Test
    fun nfcEquivalentStringsAreZeroCer() {
        val nfc = "\u00E9"
        val nfd = "e\u0301"
        assertEquals(0.0, CharacterErrorRate.cer(nfd, nfc), 0.0)
        assertEquals(0.0, CharacterErrorRate.cer(nfc, nfd), 0.0)
    }

    @Test
    fun emptyReference() {
        assertEquals(0.0, CharacterErrorRate.cer("", ""), 0.0)
        assertEquals(0.0, CharacterErrorRate.cer("   ", " "), 0.0)
        assertEquals(1.0, CharacterErrorRate.cer("有", ""), 0.0)
        assertEquals(1.0, CharacterErrorRate.cer("有", "  "), 0.0)
    }

    @Test
    fun tinyAsrFixtureMeanCerPassesFivePercent() {
        val json = javaClass.getResourceAsStream("/eval/asr-gold.json")!!.bufferedReader().use { it.readText() }
        val pairs = Json.parseToJsonElement(json).jsonObject.getValue("pairs").jsonArray.map { el ->
            val obj = el.jsonObject
            obj.getValue("hypothesis").jsonPrimitive.content to obj.getValue("reference").jsonPrimitive.content
        }
        val mean = CharacterErrorRate.mean(pairs)
        val passed = mean <= 0.05
        println("ASR fixture mean CER=$mean passed=$passed (threshold 0.05)")
        assertTrue("mean CER $mean should be <= 0.05", passed)
    }

    @Test
    fun missingFullAsrWavSetIsSkipped() {
        if (FullEvalSet.isPresent()) {
            println("full ASR set present at ${FullEvalSet.wavDir().absolutePath}; skip CI-only absence check")
            return
        }
        println("full ASR set missing; skip (not a failure)")
    }
}
