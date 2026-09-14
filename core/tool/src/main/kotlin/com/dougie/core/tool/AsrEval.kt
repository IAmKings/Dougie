package com.dougie.core.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AsrEvalItem(
    val id: String,
    val reference: String,
    val wav: String? = null,
    val hypothesis: String? = null,
    val vadOk: Boolean? = null,
)

/**
 * Rule D report. [toString] is counts and rates only — never reference or hypothesis text.
 */
data class AsrEvalReport(
    val nLabeled: Int,
    val nScored: Int,
    val nUnscored: Int,
    val meanCer: Double,
    val successRate: Double,
    val vadApplied: Boolean,
    val ruleDPassed: Boolean,
) {
    override fun toString(): String =
        "asr nLabeled=$nLabeled nScored=$nScored nUnscored=$nUnscored " +
            "meanCer=$meanCer successRate=$successRate vadApplied=$vadApplied ruleDPassed=$ruleDPassed"
}

/**
 * JSONL rule D runner. Does not read wav or call sherpa/ORT.
 *
 * One object per non-blank line: required `id` / `reference`; optional `wav`, `hypothesis`, `vadOk`.
 * Missing hypothesis is unscored (excluded from mean CER).
 */
object AsrEval {
    const val MIN_N = 500
    const val CER_LIMIT = 0.05
    const val SUCCESS_RATE_LIMIT = 0.95

    fun loadJsonl(text: String): List<AsrEvalItem> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val obj = Json.parseToJsonElement(line).jsonObject
                AsrEvalItem(
                    id = obj.getValue("id").jsonPrimitive.content,
                    reference = obj.getValue("reference").jsonPrimitive.content,
                    wav = obj.optionalString("wav"),
                    hypothesis = obj.optionalHypothesis(),
                    vadOk = obj.optionalBoolean("vadOk"),
                )
            }
            .toList()

    fun report(items: List<AsrEvalItem>): AsrEvalReport {
        val nLabeled = items.size
        val scored = items.filter { it.hypothesis != null }
        val nScored = scored.size
        val nUnscored = nLabeled - nScored
        val meanCer =
            if (nScored == 0) {
                0.0
            } else {
                scored.sumOf { CharacterErrorRate.cer(it.hypothesis!!, it.reference) } / nScored
            }
        val vadApplied = nScored > 0 && scored.all { it.vadOk != null }
        val nSuccess = scored.count { item ->
            val cer = CharacterErrorRate.cer(item.hypothesis!!, item.reference)
            cer <= CER_LIMIT && (!vadApplied || item.vadOk == true)
        }
        val successRate = if (nScored == 0) 0.0 else nSuccess.toDouble() / nScored
        val ruleDPassed =
            nLabeled >= MIN_N &&
                nScored >= MIN_N &&
                meanCer <= CER_LIMIT &&
                successRate >= SUCCESS_RATE_LIMIT &&
                vadApplied
        return AsrEvalReport(
            nLabeled = nLabeled,
            nScored = nScored,
            nUnscored = nUnscored,
            meanCer = meanCer,
            successRate = successRate,
            vadApplied = vadApplied,
            ruleDPassed = ruleDPassed,
        )
    }

    private fun JsonObject.optionalString(key: String): String? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.optionalHypothesis(): String? {
        val el = this["hypothesis"] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.contentOrNull
    }

    private fun JsonObject.optionalBoolean(key: String): Boolean? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.booleanOrNull
    }
}
