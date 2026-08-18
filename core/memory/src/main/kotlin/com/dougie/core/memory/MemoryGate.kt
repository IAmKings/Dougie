package com.dougie.core.memory

import com.dougie.core.model.GateResult
import com.dougie.core.model.MemoryEntry
import java.util.UUID

class MemoryGate(
    private val store: MemoryStore,
    private val enabled: () -> Boolean,
) {
    suspend fun ingest(userInput: String, assistantText: String?, sourceTaskId: String): GateResult {
        if (!enabled()) return GateResult.SkippedDisabled
        val text = userInput.trim()
        if (text.isEmpty()) return GateResult.SkippedNoFact
        if (looksSensitive(text) || looksSensitive(assistantText.orEmpty())) {
            return GateResult.SkippedSensitive
        }
        if (userDeclinedMemory(text)) return GateResult.SkippedNoFact
        if (text.length >= MAX_FACT_CHARS) return GateResult.SkippedNoFact
        if (!looksLikeDurableFact(text)) return GateResult.SkippedNoFact
        val duplicate = store.list().any { it.content.trim() == text }
        if (duplicate) return GateResult.SkippedDuplicate
        val now = System.currentTimeMillis()
        val entry = MemoryEntry(
            id = UUID.randomUUID().toString(),
            type = "fact",
            content = text,
            source = sourceLabel(sourceTaskId, text),
            confidence = 0.8f,
            createdAt = now,
            updatedAt = now,
        )
        store.upsert(entry)
        return GateResult.Stored(entry)
    }

    companion object {
        private const val MAX_FACT_CHARS = 200
        private val FACT_MARKERS = listOf("我叫", "我是", "我住", "我喜欢")
        private val QUESTION_MARKERS = listOf("哪里", "哪儿", "什么", "吗", "呢")
        private val DECLINE_MARKERS = listOf("不要记住", "别记住", "don't remember", "do not remember")

        fun looksSensitive(text: String): Boolean {
            if (text.isBlank()) return false
            val lower = text.lowercase()
            if (lower.contains("sk-")) return true
            if (text.contains("密码是")) return true
            if (lower.contains("password") && (lower.contains("是") || lower.contains("is"))) return true
            if (lower.contains("data:image") || lower.contains("base64,")) return true
            if (Regex("""\b(?:\d[ -]?){13,19}\b""").containsMatchIn(text)) return true
            return false
        }

        internal fun looksLikeDurableFact(text: String): Boolean {
            if (looksLikeQuestion(text)) return false
            return FACT_MARKERS.any { text.contains(it) }
        }

        internal fun looksLikeQuestion(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.contains('？') || trimmed.contains('?')) return true
            return QUESTION_MARKERS.any { trimmed.contains(it) }
        }

        private fun userDeclinedMemory(text: String): Boolean {
            val lower = text.lowercase()
            return DECLINE_MARKERS.any { lower.contains(it) }
        }

        internal fun sourceLabel(sourceTaskId: String, userInput: String): String {
            val quote = userInput.trim().take(40)
            return "$sourceTaskId · $quote"
        }
    }
}
