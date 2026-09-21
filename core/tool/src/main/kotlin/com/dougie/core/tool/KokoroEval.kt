package com.dougie.core.tool

import com.dougie.core.model.AgentException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil

data class KokoroEvalItem(
    val id: String,
    val text: String,
    val synthMs: Long? = null,
    val audioDurationMs: Long? = null,
    val numThreads: Int? = null,
    val naturalnessOk: Boolean? = null,
)

data class KokoroSynthTiming(
    val synthMs: Long,
    val audioDurationMs: Long,
    val numThreads: Int = 1,
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
 * Device collection: [loadGold] / [runForward] / [writeJsonl] / [markNaturalnessOk].
 */
object KokoroEval {
    const val MIN_N = 5
    const val RTF_LIMIT = 1.0
    const val MANIFEST_RELATIVE = "eval/tts/kokoro-rtf.jsonl"
    const val GOLD_RESOURCE = "/eval/kokoro-gold.jsonl"

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

    fun loadGold(): List<KokoroEvalItem> {
        val stream = KokoroEval::class.java.getResourceAsStream(GOLD_RESOURCE)
            ?: error("missing $GOLD_RESOURCE")
        return loadGold(stream)
    }

    fun loadGold(stream: InputStream): List<KokoroEvalItem> =
        stream.bufferedReader(Charsets.UTF_8).use { reader -> loadJsonl(reader.readText()) }

    fun report(items: List<KokoroEvalItem>): KokoroEvalReport {
        val nLabeled = items.size
        val scored = items.filter { isScored(it) }
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

    fun isScored(item: KokoroEvalItem): Boolean =
        item.synthMs != null && item.audioDurationMs != null && item.audioDurationMs > 0 && item.synthMs >= 0

    suspend fun runForward(
        items: List<KokoroEvalItem>,
        synth: suspend (text: String) -> KokoroSynthTiming,
    ): Pair<List<KokoroEvalItem>, KokoroEvalReport> {
        val out = items.map { item ->
            try {
                val timing = synth(item.text)
                item.copy(
                    synthMs = timing.synthMs,
                    audioDurationMs = timing.audioDurationMs,
                    numThreads = timing.numThreads,
                    naturalnessOk = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: AgentException) {
                item
            } catch (_: Exception) {
                item
            }
        }
        return out to report(out)
    }

    fun writeJsonl(file: File, items: List<KokoroEvalItem>) {
        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            items.forEach { item ->
                writer.appendLine(itemLine(item))
            }
        }
    }

    fun markNaturalnessOk(items: List<KokoroEvalItem>): List<KokoroEvalItem> =
        items.map { item ->
            if (isScored(item)) item.copy(naturalnessOk = true) else item
        }

    private fun itemLine(item: KokoroEvalItem): String =
        buildJsonObject {
            put("id", item.id)
            put("text", item.text)
            item.synthMs?.let { put("synthMs", it) }
            item.audioDurationMs?.let { put("audioDurationMs", it) }
            item.numThreads?.let { put("numThreads", it) }
            item.naturalnessOk?.let { put("naturalnessOk", it) }
        }.toString()

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
