package com.dougie.core.runtime

fun interface AuditLog {
    fun record(taskId: String, toolName: String, outcome: String)
}

object NoOpAuditLog : AuditLog {
    override fun record(taskId: String, toolName: String, outcome: String) = Unit
}
