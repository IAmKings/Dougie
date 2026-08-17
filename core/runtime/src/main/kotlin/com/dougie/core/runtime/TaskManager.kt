package com.dougie.core.runtime

import com.dougie.core.model.AgentTask
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
        if (current != null && current.status != com.dougie.core.model.TaskStatus.COMPLETED &&
            current.status != com.dougie.core.model.TaskStatus.FAILED
        ) {
            return
        }
        running?.cancel()
        val created = AgentTask(
            taskId = UUID.randomUUID().toString(),
            input = trimmed,
        )
        _task.value = created
        running = scope.launch(dispatcher) {
            loopEngine.run(created) { snapshot ->
                _task.value = snapshot
            }
        }
    }
}
