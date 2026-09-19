package com.dougie.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.runtime.AuditLog
import com.dougie.core.runtime.TaskManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebugViewModel(
    private val taskManager: TaskManager,
    private val auditLog: AuditLog,
    private val runRuleEEval: suspend () -> String,
    private val loadLastRuleE: suspend () -> String?,
) : ViewModel() {
    private val auditRows = MutableStateFlow<List<DebugAuditRow>>(emptyList())
    private val ruleEBusy = MutableStateFlow(false)
    private val ruleEMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DebugUiState> = combine(
        taskManager.task,
        auditRows,
        ruleEBusy,
        ruleEMessage,
    ) { task, rows, busy, message ->
        DebugUiState(
            task = task?.toDebugTaskSnapshot(),
            auditRows = rows,
            ruleEBusy = busy,
            ruleEMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugUiState())

    init {
        refresh()
        viewModelScope.launch {
            val last = try {
                loadLastRuleE()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@launch
            if (ruleEBusy.value) return@launch
            ruleEMessage.compareAndSet(null, last)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            auditRows.value = auditLog.listRecent(50).map { it.toDebugAuditRow() }
        }
    }

    fun runRuleE() {
        if (!ruleEBusy.compareAndSet(false, true)) return
        viewModelScope.launch {
            ruleEMessage.value = null
            try {
                ruleEMessage.value = runRuleEEval()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentException) {
                ruleEMessage.value = e.userMessage
            } catch (_: Exception) {
                ruleEMessage.value = UserFacingErrors.INTENT_FAILED
            } finally {
                ruleEBusy.value = false
            }
        }
    }

    class Factory(
        private val taskManager: TaskManager,
        private val auditLog: AuditLog,
        private val runRuleEEval: suspend () -> String,
        private val loadLastRuleE: suspend () -> String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DebugViewModel(taskManager, auditLog, runRuleEEval, loadLastRuleE) as T
        }
    }
}
