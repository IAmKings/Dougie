package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.exp

data class IntentTokenizerSpec(
    val dim: Int,
    val ngramMin: Int,
    val ngramMax: Int,
)

/**
 * Feature hashing of Unicode character n-grams, matching tokenizer.json:
 * algorithm `char_ngram_fnv1a32_hash_bag`.
 *
 * For each n in [ngram_min, ngram_max] and each substring of n chars, hash the
 * UTF-8 bytes with FNV-1a 32-bit, then `features[hash % dim] += 1`.
 */
object IntentHashBagFeaturizer {
    private const val FNV_OFFSET = 0x811c9dc5.toInt()
    private const val FNV_PRIME = 0x01000193

    fun loadSpec(tokenizerFile: File): IntentTokenizerSpec {
        val root = Json.parseToJsonElement(tokenizerFile.readText()).jsonObject
        val dim = root.getValue("dim").jsonPrimitive.int
        val nmin = root.getValue("ngram_min").jsonPrimitive.int
        val nmax = root.getValue("ngram_max").jsonPrimitive.int
        if (dim <= 0 || nmin <= 0 || nmax < nmin) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        return IntentTokenizerSpec(dim = dim, ngramMin = nmin, ngramMax = nmax)
    }

    fun featurize(text: String, spec: IntentTokenizerSpec): FloatArray {
        val features = FloatArray(spec.dim)
        val n = text.length
        for (size in spec.ngramMin..spec.ngramMax) {
            if (size > n) continue
            var start = 0
            while (start + size <= n) {
                val bytes = text.substring(start, start + size).toByteArray(Charsets.UTF_8)
                val index = ((fnv1a32(bytes).toLong() and 0xffffffffL) % spec.dim.toLong()).toInt()
                features[index] += 1f
                start += 1
            }
        }
        return features
    }

    internal fun fnv1a32(bytes: ByteArray): Int {
        var hash = FNV_OFFSET
        for (b in bytes) {
            hash = hash xor (b.toInt() and 0xff)
            hash *= FNV_PRIME
        }
        return hash
    }
}

class OnnxIntentEngine(
    private val modelDir: File,
    private val nativeAvailable: () -> Boolean,
    private val infer: (File, FloatArray) -> FloatArray,
) : IntentEngine {
    override fun isReady(): Boolean = IntentModelLayout.isPresent(modelDir) && nativeAvailable()

    override suspend fun classify(text: String): IntentHit {
        if (!IntentModelLayout.isPresent(modelDir)) {
            throw AgentException(UserFacingErrors.INTENT_MODEL_MISSING)
        }
        if (!nativeAvailable()) {
            throw AgentException(UserFacingErrors.INTENT_ENGINE_NOT_READY)
        }
        val labels = loadLabels(File(modelDir, IntentModelLayout.LABELS_FILE))
        val spec = try {
            IntentHashBagFeaturizer.loadSpec(File(modelDir, IntentModelLayout.TOKENIZER_FILE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            throw e
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        val features = IntentHashBagFeaturizer.featurize(text, spec)
        val logits = try {
            infer(modelDir, features)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            throw e
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        if (logits.isEmpty() || logits.size != labels.size || logits.any { it.isNaN() }) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        val probs = softmax(logits)
        var best = 0
        for (i in 1 until probs.size) {
            if (probs[i] > probs[best]) best = i
        }
        val intent = labels[best]
        return IntentHit(
            intent = intent,
            slots = emptyMap(),
            route = routeFor(intent),
            confidence = probs[best],
        )
    }

    private fun loadLabels(file: File): List<String> {
        val labels = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (labels.isEmpty()) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        return labels
    }

    private fun softmax(logits: FloatArray): DoubleArray {
        var max = logits[0].toDouble()
        for (i in 1 until logits.size) {
            val v = logits[i].toDouble()
            if (v > max) max = v
        }
        val exps = DoubleArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            val e = exp(logits[i].toDouble() - max)
            exps[i] = e
            sum += e
        }
        if (sum <= 0.0 || sum.isNaN()) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        for (i in exps.indices) {
            exps[i] /= sum
        }
        return exps
    }

    private fun routeFor(intent: String): String = when (intent) {
        "query_time" -> "time"
        "query_battery" -> "battery"
        "query_calendar", "create_calendar" -> "calendar"
        "query_location" -> "location"
        "clipboard_read", "clipboard_write" -> "clipboard"
        "open_app" -> "app"
        "screen_capture" -> "screen"
        "speech_input" -> "speech"
        else -> intent
    }
}
