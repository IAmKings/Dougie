package com.dougie.core.model

data class MemoryEntry(
    val id: String,
    val type: String = "fact",
    val content: String,
    val source: String,
    val confidence: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val embedding: ByteArray? = null,
)

data class MemoryCandidate(
    val content: String,
    val sourceTaskId: String,
    val sourceQuote: String,
)

sealed class GateResult {
    data class Stored(val entry: MemoryEntry) : GateResult()
    data object SkippedSensitive : GateResult()
    data object SkippedDisabled : GateResult()
    data object SkippedDuplicate : GateResult()
    data object SkippedNoFact : GateResult()
}
