package com.dougie.core.runtime

import com.dougie.core.model.AgentTask
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TaskStore {
    suspend fun upsert(task: AgentTask)
    suspend fun listRecent(limit: Int = 50): List<AgentTask>
    suspend fun listByConversation(conversationId: String): List<AgentTask>
    suspend fun deleteByConversation(conversationId: String): Int
    suspend fun deleteByTaskId(taskId: String): Int
    suspend fun searchCompletedTurns(
        query: String,
        excludeTaskIds: Set<String> = emptySet(),
        limit: Int = 3,
    ): List<AgentTask>
    suspend fun searchHistory(query: String, limit: Int = Int.MAX_VALUE): List<AgentTask>
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

    override suspend fun listByConversation(conversationId: String): List<AgentTask> = mutex.withLock {
        recentIds.mapNotNull { byId[it] }.filter { it.conversationId == conversationId }
    }

    override suspend fun deleteByConversation(conversationId: String): Int = mutex.withLock {
        if (conversationId.isBlank() || conversationId == ConversationIds.DEFAULT) return@withLock 0
        val ids = byId.values.filter { it.conversationId == conversationId }.map { it.taskId }
        ids.forEach { taskId ->
            byId.remove(taskId)
            recentIds.remove(taskId)
        }
        ids.size
    }

    override suspend fun deleteByTaskId(taskId: String): Int = mutex.withLock {
        if (taskId.isBlank()) return@withLock 0
        val removed = byId.remove(taskId) != null
        recentIds.remove(taskId)
        if (removed) 1 else 0
    }

    override suspend fun searchCompletedTurns(
        query: String,
        excludeTaskIds: Set<String>,
        limit: Int,
    ): List<AgentTask> = mutex.withLock {
        val needles = conversationSearchNeedles(query)
        if (needles.isEmpty() || limit <= 0) return@withLock emptyList()
        recentIds.asReversed().asSequence()
            .mapNotNull { byId[it] }
            .filter { it.matchesCompletedTurnNeedles(needles, excludeTaskIds) }
            .take(limit)
            .toList()
    }

    override suspend fun searchHistory(query: String, limit: Int): List<AgentTask> = mutex.withLock {
        val needles = conversationSearchNeedles(query)
        if (needles.isEmpty() || limit <= 0) return@withLock emptyList()
        recentIds.asReversed().asSequence()
            .mapNotNull { byId[it] }
            .filter { it.matchesHistoryNeedles(needles) }
            .take(limit)
            .toList()
    }
}

private val CONVERSATION_SEARCH_STOPWORDS = setOf(
    "这个", "那个", "这些", "那些", "什么", "怎么", "哪个", "哪些",
    "一下", "一个", "我们", "你们", "他们", "不是", "可以", "现在",
    "如果", "因为", "所以", "然后", "还有", "问题", "怎么了",
)

fun conversationSearchNeedles(query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val needles = LinkedHashSet<String>()
    Regex("[A-Za-z0-9]{2,}").findAll(trimmed).forEach { match ->
        needles += match.value
    }
    Regex("[\\u4e00-\\u9fff]{2,}").findAll(trimmed).forEach { match ->
        val run = match.value
        if (run !in CONVERSATION_SEARCH_STOPWORDS) needles += run
    }
    return needles.toList()
}

fun AgentTask.matchesCompletedTurnNeedles(
    needles: List<String>,
    excludeTaskIds: Set<String>,
): Boolean {
    if (needles.isEmpty()) return false
    if (taskId in excludeTaskIds) return false
    if (status != TaskStatus.COMPLETED) return false
    val answer = finalAnswer?.trim().orEmpty()
    if (answer.isEmpty()) return false
    val haystack = input + finalAnswer.orEmpty()
    return needles.any { needle -> haystack.contains(needle, ignoreCase = true) }
}

fun AgentTask.matchesHistoryNeedles(needles: List<String>): Boolean {
    if (needles.isEmpty()) return false
    if (status != TaskStatus.COMPLETED && status != TaskStatus.FAILED) return false
    val haystack = input + finalAnswer.orEmpty() + lastError.orEmpty()
    return needles.any { needle -> haystack.contains(needle, ignoreCase = true) }
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
    ).stampEndedAtIfTerminal()
    store.upsert(failed)
    return failed
}

internal fun AgentTask.stampEndedAtIfTerminal(
    nowMs: Long = System.currentTimeMillis(),
): AgentTask {
    if (endedAt != null) return this
    if (status != TaskStatus.COMPLETED && status != TaskStatus.FAILED) return this
    return copy(endedAt = nowMs)
}
