package com.dougie.core.runtime

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TaskStore {
    suspend fun upsert(task: AgentTask)
    suspend fun listRecent(limit: Int = 50): List<AgentTask>
}

class InMemoryTaskStore : TaskStore {
    private val mutex = Mutex()
    private val byId = LinkedHashMap<String, AgentTask>()
    private val recentIds = ArrayList<String>()

    override suspend fun upsert(task: AgentTask) {
        mutex.withLock {
            byId[task.taskId] = task
            recentIds.remove(task.taskId)
            recentIds.add(task.taskId)
        }
    }

    override suspend fun listRecent(limit: Int): List<AgentTask> = mutex.withLock {
        recentIds.asReversed().take(limit).mapNotNull { byId[it] }
    }
}

suspend fun recoverInterrupted(store: TaskStore): AgentTask? {
    val latest = store.listRecent(1).firstOrNull() ?: return null
    if (latest.status == TaskStatus.COMPLETED || latest.status == TaskStatus.FAILED) {
        return null
    }
    val failed = latest.copy(
        status = TaskStatus.FAILED,
        lastError = UserFacingErrors.INTERRUPTED,
        streamingText = null,
    )
    store.upsert(failed)
    return failed
}
