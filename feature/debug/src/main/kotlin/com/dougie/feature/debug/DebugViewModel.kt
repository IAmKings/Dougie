package com.dougie.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.runtime.AuditLog
import com.dougie.core.runtime.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebugViewModel(
    private val taskManager: TaskManager,
    private val auditLog: AuditLog,
) : ViewModel() {
    private val auditRows = MutableStateFlow<List<DebugAuditRow>>(emptyList())

    val uiState: StateFlow<DebugUiState> = combine(taskManager.task, auditRows) { task, rows ->
        DebugUiState(
            task = task?.toDebugTaskSnapshot(),
            auditRows = rows,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            auditRows.value = auditLog.listRecent(50).map { it.toDebugAuditRow() }
        }
    }

    class Factory(
        private val taskManager: TaskManager,
        private val auditLog: AuditLog,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DebugViewModel(taskManager, auditLog) as T
        }
    }
}
