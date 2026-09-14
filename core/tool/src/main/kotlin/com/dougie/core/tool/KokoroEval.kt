package com.dougie.core.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.ceil

data class KokoroEvalItem(
    val id: String,
    val text: String,
    val synthMs: Long? = null,
    val audioDurationMs: Long? = null,
    val numThreads: Int? = null,
    val naturalnessOk: Boolean? = null,
)

/**
 * Rule B report. [toString] is counts and rates only — never utterance text or PCM.
 */
data class KokoroEvalReport(
    val nLabeled: Int,
    val nScored: Int,
    val nUnscored: Int,
    val p95Rtf: Double,
    val threadsApplied: Boolean,
    val naturalnessApplied: Boolean,
    val ruleBPassed: Boolean,
) {
    override fun toString(): String =
        "kokoro nLabeled=$nLabeled nScored=$nScored nUnscored=$nUnscored " +
            "p95Rtf=$p95Rtf threadsApplied=$threadsApplied naturalnessApplied=$naturalnessApplied " +
            "ruleBPassed=$ruleBPassed"
}

/**
 * JSONL rule B runner. Does not call sherpa/ORT or read PCM.
 *
 * One object per non-blank line: required `id` / `text`; optional `synthMs`,
 * `audioDurationMs`, `numThreads`, `naturalnessOk`. Absent and JSON null are missing.
 * Missing or non-positive duration (or negative synth) is unscored.
 */
object KokoroEval {
    const val MIN_N = 5
    const val RTF_LIMIT = 1.0
    const val MANIFEST_RELATIVE = "eval/tts/kokoro-rtf.jsonl"

    fun loadJsonl(text: String): List<KokoroEvalItem> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val obj = Json.parseToJsonElement(line).jsonObject
                KokoroEvalItem(
                    id = obj.getValue("id").jsonPrimitive.content,
                    text = obj.getValue("text").jsonPrimitive.content,
                    synthMs = obj.optionalLong("synthMs"),
                    audioDurationMs = obj.optionalLong("audioDurationMs"),
                    numThreads = obj.optionalInt("numThreads"),
                    naturalnessOk = obj.optionalBoolean("naturalnessOk"),
                )
            }
            .toList()

    fun report(items: List<KokoroEvalItem>): KokoroEvalReport {
        val nLabeled = items.size
        val scored = items.filter { it.isScored() }
        val nScored = scored.size
        val nUnscored = nLabeled - nScored
        val rtfs = scored.map { rtf(it.synthMs!!, it.audioDurationMs!!) }
        val p95Rtf = p95(rtfs)
        val threadsApplied = nScored > 0 && scored.all { it.numThreads == 1 }
        val naturalnessApplied = nScored > 0 && scored.all { it.naturalnessOk == true }
        val ruleBPassed =
            nLabeled >= MIN_N &&
                nScored >= MIN_N &&
                p95Rtf <= RTF_LIMIT &&
                threadsApplied &&
                naturalnessApplied
        return KokoroEvalReport(
            nLabeled = nLabeled,
            nScored = nScored,
            nUnscored = nUnscored,
            p95Rtf = p95Rtf,
            threadsApplied = threadsApplied,
            naturalnessApplied = naturalnessApplied,
            ruleBPassed = ruleBPassed,
        )
    }

    fun rtf(synthMs: Long, audioDurationMs: Long): Double {
        if (audioDurationMs <= 0) return 0.0
        return synthMs.toDouble() / audioDurationMs
    }

    private fun KokoroEvalItem.isScored(): Boolean =
        synthMs != null && audioDurationMs != null && audioDurationMs > 0 && synthMs >= 0

    private fun p95(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ceil(0.95 * sorted.size).toInt() - 1
        return sorted[index]
    }

    private fun JsonObject.optionalLong(key: String): Long? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.longOrNull
    }

    private fun JsonObject.optionalInt(key: String): Int? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.intOrNull
    }

    private fun JsonObject.optionalBoolean(key: String): Boolean? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.booleanOrNull
    }
}
