package com.dougie.core.runtime

data class AuditEntry(
    val taskId: String,
    val toolName: String,
    val outcome: String,
    val createdAt: Long,
)

fun interface AuditLog {
    fun record(taskId: String, toolName: String, outcome: String)

    suspend fun listRecent(limit: Int = 50): List<AuditEntry> = emptyList()
}

object NoOpAuditLog : AuditLog {
    override fun record(taskId: String, toolName: String, outcome: String) = Unit
}
