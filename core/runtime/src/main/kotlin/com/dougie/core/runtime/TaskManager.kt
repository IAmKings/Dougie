package com.dougie.core.runtime

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TaskManager(
    private val loopEngine: LoopEngine,
    private val dispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {
    private val _task = MutableStateFlow<AgentTask?>(null)
    val task: StateFlow<AgentTask?> = _task.asStateFlow()

    private var running: Job? = null

    fun submit(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val current = _task.value
        if (current != null && current.status != TaskStatus.COMPLETED &&
            current.status != TaskStatus.FAILED
        ) {
            return
        }
        val created = AgentTask(
            taskId = UUID.randomUUID().toString(),
            input = trimmed,
        )
        _task.value = created
        running = scope.launch(dispatcher) {
            try {
                loopEngine.run(created) { snapshot ->
                    _task.value = snapshot
                }
            } catch (e: CancellationException) {
                markCancelled()
                throw e
            }
        }
    }

    fun confirm() {
        loopEngine.confirm()
    }

    fun reject() {
        loopEngine.reject()
    }

    fun cancel() {
        running?.cancel()
    }

    private fun markCancelled() {
        val current = _task.value ?: return
        if (current.status == TaskStatus.COMPLETED || current.status == TaskStatus.FAILED) return
        _task.value = current.copy(
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.CANCELLED,
            streamingText = null,
        )
    }
}
