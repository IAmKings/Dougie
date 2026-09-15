package com.dougie.feature.debug

import com.dougie.core.model.AgentTask
import com.dougie.core.runtime.AuditEntry

data class DebugTaskSnapshot(
    val taskId: String,
    val status: String,
    val loopCount: Int,
    val lastError: String?,
    val completionPath: String,
)

data class DebugAuditRow(
    val taskId: String,
    val toolName: String,
    val outcome: String,
    val createdAt: Long,
)

const val RULE_E_ACTION_LABEL = "评测意图规则 E"

data class DebugUiState(
    val task: DebugTaskSnapshot? = null,
    val auditRows: List<DebugAuditRow> = emptyList(),
    val ruleEBusy: Boolean = false,
    val ruleEMessage: String? = null,
)

fun AgentTask.toDebugTaskSnapshot(): DebugTaskSnapshot = DebugTaskSnapshot(
    taskId = taskId,
    status = status.name,
    loopCount = loopCount,
    lastError = lastError,
    completionPath = completionPath?.toUserLabel() ?: "无",
)

fun AuditEntry.toDebugAuditRow(): DebugAuditRow = DebugAuditRow(
    taskId = taskId,
    toolName = toolName,
    outcome = outcome,
    createdAt = createdAt,
)
