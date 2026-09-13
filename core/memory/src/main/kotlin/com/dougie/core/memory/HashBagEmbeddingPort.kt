package com.dougie.core.memory

import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Character n-gram hash bag, same FNV-1a scheme as intent hashbag tokenizers.
 * Ready only when [tokenizer.json] is present and parses.
 */
class HashBagEmbeddingPort(
    private val modelDir: File,
) : EmbeddingPort {
    override fun isReady(): Boolean {
        val spec = loadSpec() ?: return false
        return spec.dim > 0 && spec.ngramMax >= spec.ngramMin
    }

    override suspend fun embed(text: String): FloatArray? {
        val spec = loadSpec() ?: return null
        return try {
            l2Normalize(featurize(text, spec))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun loadSpec(): HashBagSpec? {
        return try {
            val file = File(modelDir, TOKENIZER_FILE)
            if (!file.isFile || file.length() <= 0L) return null
            parseHashBagSpec(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val TOKENIZER_FILE = "tokenizer.json"
    }
}

internal data class HashBagSpec(
    val dim: Int,
    val ngramMin: Int,
    val ngramMax: Int,
)

internal fun parseHashBagSpec(json: String): HashBagSpec? {
    if (!json.contains("char_ngram") && !json.contains("hash_bag")) return null
    val dim = intField(json, "dim") ?: return null
    val nmin = intField(json, "ngram_min") ?: return null
    val nmax = intField(json, "ngram_max") ?: return null
    if (dim <= 0 || nmin <= 0 || nmax < nmin) return null
    return HashBagSpec(dim, nmin, nmax)
}

private fun intField(json: String, name: String): Int? {
    val match = Regex(""""$name"\s*:\s*(-?\d+)""").find(json) ?: return null
    return match.groupValues[1].toIntOrNull()
}

private const val FNV_OFFSET = 0x811c9dc5.toInt()
private const val FNV_PRIME = 0x01000193

internal fun featurize(text: String, spec: HashBagSpec): FloatArray {
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

private fun fnv1a32(bytes: ByteArray): Int {
    var hash = FNV_OFFSET
    for (b in bytes) {
        hash = hash xor (b.toInt() and 0xff)
        hash *= FNV_PRIME
    }
    return hash
}
