package com.dougie.feature.debug

import com.dougie.core.model.AgentTask
import com.dougie.core.runtime.AuditEntry

data class DebugTaskSnapshot(
    val taskId: String,
    val status: String,
    val loopCount: Int,
    val lastError: String?,
)

data class DebugAuditRow(
    val taskId: String,
    val toolName: String,
    val outcome: String,
    val createdAt: Long,
)

data class DebugUiState(
    val task: DebugTaskSnapshot? = null,
    val auditRows: List<DebugAuditRow> = emptyList(),
)

fun AgentTask.toDebugTaskSnapshot(): DebugTaskSnapshot = DebugTaskSnapshot(
    taskId = taskId,
    status = status.name,
    loopCount = loopCount,
    lastError = lastError,
)

fun AuditEntry.toDebugAuditRow(): DebugAuditRow = DebugAuditRow(
    taskId = taskId,
    toolName = toolName,
    outcome = outcome,
    createdAt = createdAt,
)
