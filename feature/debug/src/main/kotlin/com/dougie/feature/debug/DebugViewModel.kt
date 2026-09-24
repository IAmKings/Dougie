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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class RuleBDebugState(
    val busy: Boolean = false,
    val message: String? = null,
    val downloaded: Long = 0L,
    val total: Long = -1L,
)

private data class ContractDebugState(
    val busy: Boolean = false,
    val message: String? = null,
    val ready: Boolean = false,
)

class DebugViewModel(
    private val taskManager: TaskManager,
    private val auditLog: AuditLog,
    private val runRuleEEval: suspend () -> String,
    private val loadLastRuleE: suspend () -> String?,
    private val runKokoroRuleB: suspend (onProgress: (Long, Long) -> Unit) -> String,
    private val loadLastKokoroRuleB: suspend () -> String?,
    private val markKokoroNaturalnessOk: suspend () -> String,
    private val contractReady: () -> Boolean,
    private val runContractEval: suspend () -> String,
    private val loadLastLocalToolContract: suspend () -> String?,
) : ViewModel() {
    private val auditRows = MutableStateFlow<List<DebugAuditRow>>(emptyList())
    private val ruleEBusy = MutableStateFlow(false)
    private val ruleEMessage = MutableStateFlow<String?>(null)
    private val ruleB = MutableStateFlow(RuleBDebugState())
    private val contract = MutableStateFlow(ContractDebugState())

    val uiState: StateFlow<DebugUiState> = combine(
        taskManager.task,
        auditRows,
        ruleEBusy,
        ruleEMessage,
        combine(ruleB, contract) { b, c -> b to c },
    ) { task, rows, eBusy, eMessage, pair ->
        val (b, c) = pair
        DebugUiState(
            task = task?.toDebugTaskSnapshot(),
            auditRows = rows,
            ruleEBusy = eBusy,
            ruleEMessage = eMessage,
            ruleBBusy = b.busy,
            ruleBMessage = b.message,
            ruleBDownloaded = b.downloaded,
            ruleBTotal = b.total,
            contractBusy = c.busy,
            contractMessage = c.message,
            contractReady = c.ready,
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
        viewModelScope.launch {
            val last = try {
                loadLastKokoroRuleB()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@launch
            ruleB.update { current ->
                if (current.busy || current.message != null) current else current.copy(message = last)
            }
        }
        viewModelScope.launch {
            val last = try {
                loadLastLocalToolContract()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            contract.update { current ->
                if (current.busy || current.message != null || last == null) current else current.copy(message = last)
            }
        }
    }

    fun refresh() {
        contract.update { it.copy(ready = contractReady()) }
        viewModelScope.launch {
            auditRows.value = auditLog.listRecent(50).map { it.toDebugAuditRow() }
        }
    }

    fun runRuleE() {
        if (ruleB.value.busy || contract.value.busy) return
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

    fun runLocalToolContract() {
        if (ruleEBusy.value || ruleB.value.busy) return
        if (!contract.value.ready) return
        val current = contract.value
        if (current.busy) return
        if (!contract.compareAndSet(current, current.copy(busy = true, message = null))) return
        viewModelScope.launch {
            try {
                val message = runContractEval()
                contract.update { it.copy(message = message, ready = contractReady()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentException) {
                contract.update { it.copy(message = e.userMessage) }
            } catch (_: Exception) {
                contract.update { it.copy(message = UserFacingErrors.CHAT_ENGINE_NOT_READY) }
            } finally {
                contract.update { it.copy(busy = false, ready = contractReady()) }
            }
        }
    }

    fun runRuleB() {
        if (ruleEBusy.value || contract.value.busy) return
        val current = ruleB.value
        if (current.busy) return
        if (!ruleB.compareAndSet(current, current.copy(busy = true, downloaded = 0L, total = -1L, message = null))) {
            return
        }
        viewModelScope.launch {
            try {
                val message = runKokoroRuleB { downloaded, total ->
                    ruleB.update { it.copy(downloaded = downloaded, total = total) }
                }
                ruleB.update { it.copy(message = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentException) {
                ruleB.update { it.copy(message = e.userMessage) }
            } catch (_: Exception) {
                ruleB.update { it.copy(message = UserFacingErrors.KOKORO_EVAL_MODEL_MISSING) }
            } finally {
                ruleB.update { it.copy(busy = false) }
            }
        }
    }

    fun markKokoroNaturalness() {
        if (ruleEBusy.value || ruleB.value.busy || contract.value.busy) return
        if (!canMarkKokoroNaturalness(ruleB.value.message)) return
        val current = ruleB.value
        if (!ruleB.compareAndSet(current, current.copy(busy = true))) return
        viewModelScope.launch {
            try {
                val message = markKokoroNaturalnessOk()
                ruleB.update { it.copy(message = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentException) {
                ruleB.update { it.copy(message = e.userMessage) }
            } catch (_: Exception) {
                ruleB.update { it.copy(message = UserFacingErrors.KOKORO_EVAL_MODEL_MISSING) }
            } finally {
                ruleB.update { it.copy(busy = false) }
            }
        }
    }

    class Factory(
        private val taskManager: TaskManager,
        private val auditLog: AuditLog,
        private val runRuleEEval: suspend () -> String,
        private val loadLastRuleE: suspend () -> String?,
        private val runKokoroRuleB: suspend (onProgress: (Long, Long) -> Unit) -> String,
        private val loadLastKokoroRuleB: suspend () -> String?,
        private val markKokoroNaturalnessOk: suspend () -> String,
        private val contractReady: () -> Boolean,
        private val runContractEval: suspend () -> String,
        private val loadLastLocalToolContract: suspend () -> String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DebugViewModel(
                taskManager,
                auditLog,
                runRuleEEval,
                loadLastRuleE,
                runKokoroRuleB,
                loadLastKokoroRuleB,
                markKokoroNaturalnessOk,
                contractReady,
                runContractEval,
                loadLastLocalToolContract,
            ) as T
        }
    }
}
