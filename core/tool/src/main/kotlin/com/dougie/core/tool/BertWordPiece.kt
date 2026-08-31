package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class BertWordPieceSpec(
    val maxLen: Int,
    val cls: String,
    val sep: String,
    val pad: String,
    val unk: String,
)

object BertWordPiece {
    fun loadSpec(tokenizerFile: File): BertWordPieceSpec {
        val root = try {
            Json.parseToJsonElement(tokenizerFile.readText()).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        val maxLen = intField(root, "max_len") ?: 32
        if (maxLen <= 2) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        return BertWordPieceSpec(
            maxLen = maxLen,
            cls = stringField(root, "cls") ?: "[CLS]",
            sep = stringField(root, "sep") ?: "[SEP]",
            pad = stringField(root, "pad") ?: "[PAD]",
            unk = stringField(root, "unk") ?: "[UNK]",
        )
    }

    fun loadVocab(vocabFile: File): Map<String, Int> {
        val lines = vocabFile.readLines()
        if (lines.isEmpty()) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        val vocab = LinkedHashMap<String, Int>(lines.size)
        lines.forEachIndexed { index, raw ->
            val token = raw.trimEnd('\r')
            if (token.isNotEmpty() && token !in vocab) {
                vocab[token] = index
            }
        }
        if (vocab.isEmpty()) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        return vocab
    }

    fun encode(text: String, spec: BertWordPieceSpec, vocab: Map<String, Int>): Pair<LongArray, LongArray> {
        val clsId = vocab[spec.cls] ?: throw AgentException(UserFacingErrors.INTENT_FAILED)
        val sepId = vocab[spec.sep] ?: throw AgentException(UserFacingErrors.INTENT_FAILED)
        val padId = vocab[spec.pad] ?: 0
        val unkId = vocab[spec.unk] ?: 0
        val pieces = ArrayList<Int>(spec.maxLen)
        pieces += clsId
        val limit = spec.maxLen - 1
        for (token in basicTokens(text)) {
            for (id in wordPieces(token, vocab, unkId)) {
                if (pieces.size >= limit) break
                pieces += id
            }
            if (pieces.size >= limit) break
        }
        if (pieces.size >= spec.maxLen) {
            pieces[spec.maxLen - 1] = sepId
        } else {
            pieces += sepId
        }
        val ids = LongArray(spec.maxLen) { padId.toLong() }
        val mask = LongArray(spec.maxLen)
        val n = pieces.size.coerceAtMost(spec.maxLen)
        for (i in 0 until n) {
            ids[i] = pieces[i].toLong()
            mask[i] = 1L
        }
        return ids to mask
    }

    private fun basicTokens(text: String): List<String> {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                out += buf.toString()
                buf.setLength(0)
            }
        }
        val punct = "，。！？、；：\"'（）【】《》,.!?;:()[]{}`"
        for (ch in text.trim()) {
            when {
                ch.isWhitespace() -> flush()
                ch in punct -> {
                    flush()
                    out += ch.toString()
                }
                ch in '\u4e00'..'\u9fff' -> {
                    flush()
                    out += ch.toString()
                }
                else -> buf.append(ch)
            }
        }
        flush()
        return out
    }

    private fun wordPieces(token: String, vocab: Map<String, Int>, unkId: Int): List<Int> {
        if (token in vocab) return listOf(vocab.getValue(token))
        if (token.length == 1) return listOf(unkId)
        val ids = ArrayList<Int>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var found: String? = null
            while (end > start) {
                val slice = if (start == 0) {
                    token.substring(start, end)
                } else {
                    "##" + token.substring(start, end)
                }
                if (slice in vocab) {
                    found = slice
                    break
                }
                end -= 1
            }
            if (found == null) {
                return listOf(unkId)
            }
            ids += vocab.getValue(found)
            start = end
        }
        return ids
    }

    private fun stringField(root: JsonObject, key: String): String? =
        (root[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun intField(root: JsonObject, key: String): Int? {
        val prim = root[key] as? JsonPrimitive ?: return null
        return prim.intOrNull ?: prim.contentOrNull?.toIntOrNull()
    }
}
