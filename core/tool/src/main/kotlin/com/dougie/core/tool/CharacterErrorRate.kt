package com.dougie.core.tool

import java.text.Normalizer
import kotlin.math.max

/**
 * Character Error Rate for PRD rule D (fixture target CER ≤ 5%).
 *
 * Both sides are NFC-normalized and whitespace is stripped (digits/latin kept).
 * Empty reference: CER is 0 if the hypothesis is also empty, otherwise 1.
 */
object CharacterErrorRate {
    fun cer(hypothesis: String, reference: String): Double {
        val hyp = normalize(hypothesis)
        val ref = normalize(reference)
        if (ref.isEmpty()) return if (hyp.isEmpty()) 0.0 else 1.0
        return levenshtein(hyp, ref).toDouble() / max(1, ref.length)
    }

    fun mean(pairs: List<Pair<String, String>>): Double {
        require(pairs.isNotEmpty())
        return pairs.sumOf { (hyp, ref) -> cer(hyp, ref) } / pairs.size
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).filterNot { it.isWhitespace() }

    internal fun levenshtein(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        val prev = IntArray(m + 1) { it }
        val curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            for (j in 0..m) prev[j] = curr[j]
        }
        return prev[m]
    }
}
