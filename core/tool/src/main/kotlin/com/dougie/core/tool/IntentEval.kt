package com.dougie.core.tool

import com.dougie.core.model.AgentException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil

data class IntentEvalItem(
    val id: String,
    val goldIntent: String,
    val modelJson: String,
)

/**
 * Parser gold report. [passed] is canned [IntentEvalItem.modelJson] accuracy — not Rule E.
 */
data class IntentEvalReport(
    val total: Int,
    val correct: Int,
    val accuracy: Double,
    val passed: Boolean,
) {
    override fun toString(): String =
        "intent accuracy=$accuracy correct=$correct/$total passed=$passed (threshold 0.90)"
}

data class IntentPredItem(
    val id: String,
    val text: String,
    val goldIntent: String,
    val predictedIntent: String? = null,
    val latencyMs: Long? = null,
)

/**
 * Rule E forward-pass report. [toString] is counts and rates only — never utterance text or intent labels.
 * [ruleEPassed] is independent of [IntentEvalReport.passed].
 */
data class IntentRuleEReport(
    val nLabeled: Int,
    val nScored: Int,
    val nUnscored: Int,
    val nClasses: Int,
    val accuracy: Double,
    val p95Ms: Long,
    val latencyApplied: Boolean,
    val ruleEPassed: Boolean,
) {
    override fun toString(): String =
        "intent nLabeled=$nLabeled nScored=$nScored nUnscored=$nUnscored nClasses=$nClasses " +
            "accuracy=$accuracy p95Ms=$p95Ms latencyApplied=$latencyApplied ruleEPassed=$ruleEPassed"
}

/**
 * JSON parser gold plus JSONL Rule E runner. Does not call ORT/JNI.
 *
 * Parser API ([loadItems] / [report] / [IntentEvalReport.passed]) stays canned modelJson.
 * One JSONL object per non-blank line: required `id` / `text` / `goldIntent`;
 * optional `predictedIntent` / `latencyMs`. Missing prediction is unscored.
 * Device collection: [loadHeldout] / [runForward] / [writeJsonl] on classpath held-out.
 */
object IntentEval {
    const val THRESHOLD = 0.90
    const val MIN_N = 88
    const val MIN_CLASSES = 10
    const val P95_LIMIT_MS = 500L
    const val PREDICTIONS_RELATIVE = "eval/intent/predictions.jsonl"
    const val HELDOUT_RESOURCE = "/intent-corpus/heldout.jsonl"

    fun loadItems(json: String): List<IntentEvalItem> {
        val root = Json.parseToJsonElement(json).jsonObject
        return root.getValue("items").jsonArray.map { el ->
            val obj = el.jsonObject
            IntentEvalItem(
                id = obj.getValue("id").jsonPrimitive.content,
                goldIntent = obj.getValue("goldIntent").jsonPrimitive.content,
                modelJson = obj.getValue("modelJson").jsonPrimitive.content,
            )
        }
    }

    fun report(items: List<IntentEvalItem>): IntentEvalReport {
        require(items.isNotEmpty())
        val correct = items.count { item ->
            try {
                IntentJsonParser.parse(item.modelJson).intent == item.goldIntent
            } catch (_: AgentException) {
                false
            }
        }
        val accuracy = correct.toDouble() / items.size
        return IntentEvalReport(
            total = items.size,
            correct = correct,
            accuracy = accuracy,
            passed = accuracy >= THRESHOLD,
        )
    }

    fun loadJsonl(text: String): List<IntentPredItem> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val obj = Json.parseToJsonElement(line).jsonObject
                IntentPredItem(
                    id = obj.getValue("id").jsonPrimitive.content,
                    text = obj.getValue("text").jsonPrimitive.content,
                    goldIntent = obj.getValue("goldIntent").jsonPrimitive.content,
                    predictedIntent = obj.optionalPredictedIntent(),
                    latencyMs = obj.optionalLong("latencyMs"),
                )
            }
            .toList()

    fun ruleEReport(items: List<IntentPredItem>): IntentRuleEReport {
        val nLabeled = items.size
        val nClasses = items.map { it.goldIntent }.toSet().size
        val scored = items.filter { it.predictedIntent != null }
        val nScored = scored.size
        val nUnscored = nLabeled - nScored
        val accuracy =
            if (nScored == 0) {
                0.0
            } else {
                scored.count { it.predictedIntent == it.goldIntent }.toDouble() / nScored
            }
        val latencyApplied = nScored > 0 && scored.all { it.latencyMs != null }
        val latencies = scored.mapNotNull { it.latencyMs }
        val p95Ms = p95(latencies)
        val ruleEPassed =
            nClasses >= MIN_CLASSES &&
                nLabeled >= MIN_N &&
                nScored >= MIN_N &&
                accuracy >= THRESHOLD &&
                latencyApplied &&
                p95Ms <= P95_LIMIT_MS
        return IntentRuleEReport(
            nLabeled = nLabeled,
            nScored = nScored,
            nUnscored = nUnscored,
            nClasses = nClasses,
            accuracy = accuracy,
            p95Ms = p95Ms,
            latencyApplied = latencyApplied,
            ruleEPassed = ruleEPassed,
        )
    }

    suspend fun timedClassify(engine: IntentEngine, text: String): Pair<String, Long> {
        val startNs = System.nanoTime()
        val hit = engine.classify(text)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        return hit.intent to elapsedMs
    }

    fun loadHeldout(): List<IntentPredItem> {
        val stream = IntentEval::class.java.getResourceAsStream(HELDOUT_RESOURCE)
            ?: error("missing $HELDOUT_RESOURCE")
        return loadHeldout(stream)
    }

    fun loadHeldout(stream: InputStream): List<IntentPredItem> =
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { index, line ->
                    val obj = Json.parseToJsonElement(line).jsonObject
                    IntentPredItem(
                        id = "h" + (index + 1).toString().padStart(3, '0'),
                        text = obj.getValue("text").jsonPrimitive.content,
                        goldIntent = obj.getValue("intent").jsonPrimitive.content,
                    )
                }
                .toList()
        }

    suspend fun runForward(
        engine: IntentEngine,
        items: List<IntentPredItem>,
    ): Pair<List<IntentPredItem>, IntentRuleEReport> {
        val scored = items.map { item ->
            try {
                val (predicted, ms) = timedClassify(engine, item.text)
                item.copy(predictedIntent = predicted, latencyMs = ms)
            } catch (e: CancellationException) {
                throw e
            } catch (_: AgentException) {
                item
            }
        }
        return scored to ruleEReport(scored)
    }

    fun writeJsonl(file: File, items: List<IntentPredItem>) {
        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            items.forEach { item ->
                writer.appendLine(predLine(item))
            }
        }
    }

    private fun predLine(item: IntentPredItem): String =
        buildJsonObject {
            put("id", item.id)
            put("text", item.text)
            put("goldIntent", item.goldIntent)
            item.predictedIntent?.let { put("predictedIntent", it) }
            item.latencyMs?.let { put("latencyMs", it) }
        }.toString()

    private fun p95(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val index = ceil(0.95 * sorted.size).toInt() - 1
        return sorted[index]
    }

    private fun JsonObject.optionalPredictedIntent(): String? {
        val el = this["predictedIntent"] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.contentOrNull
    }

    private fun JsonObject.optionalLong(key: String): Long? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.longOrNull
    }
}
