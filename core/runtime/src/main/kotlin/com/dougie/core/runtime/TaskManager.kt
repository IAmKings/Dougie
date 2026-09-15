package com.dougie.core.runtime

import com.dougie.core.model.AgentTask
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentMeta
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ScreenFrameStore
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
    private val taskStore: TaskStore? = null,
    private val screenFrames: ScreenFrameStore? = null,
    private val onTaskFinished: () -> Unit = {},
    private val conversation: ConversationPointer = InMemoryConversationPointer(),
) {
    private val _task = MutableStateFlow<AgentTask?>(null)
    val task: StateFlow<AgentTask?> = _task.asStateFlow()

    private val _transcript = MutableStateFlow<List<AgentTask>>(emptyList())
    val transcript: StateFlow<List<AgentTask>> = _transcript.asStateFlow()

    private var running: Job? = null

    fun seed(task: AgentTask) {
        _task.value = task
    }

    fun submit(
        input: String,
        attachedCaptureId: String? = null,
        attachedWidth: Int? = null,
        attachedHeight: Int? = null,
        attachments: List<AttachmentMeta> = emptyList(),
        speakReply: Boolean = false,
    ) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        if (isBusy()) return
        val lastScreen = attachments.lastOrNull { it.kind == AttachmentKind.SCREEN }
        val captureId = lastScreen?.id?.takeIf { it.isNotBlank() }
            ?: attachedCaptureId?.takeIf { it.isNotBlank() }
        val frames = screenFrames
        if (captureId != null) {
            frames?.pinId(captureId)
        }
        val created = AgentTask(
            taskId = UUID.randomUUID().toString(),
            input = trimmed,
            attachedCaptureId = captureId,
            attachedWidth = (lastScreen?.width ?: attachedWidth)?.takeIf { it > 0 },
            attachedHeight = (lastScreen?.height ?: attachedHeight)?.takeIf { it > 0 },
            attachments = attachments,
            speakReply = speakReply,
            startedAt = System.currentTimeMillis(),
            conversationId = conversation.currentId(),
        )
        _task.value = created
        running = scope.launch(dispatcher) {
            persist(created)
            reloadTranscript()
            try {
                loopEngine.run(created) { snapshot ->
                    val stamped = snapshot.stampEndedAtIfTerminal()
                    _task.value = stamped
                    persist(stamped)
                }
            } catch (e: CancellationException) {
                markCancelled()
                throw e
            } finally {
                screenFrames?.clearPin()
                onTaskFinished()
            }
        }
    }

    fun newConversation() {
        if (isBusy()) return
        if (_task.value == null && _transcript.value.isEmpty()) return
        conversation.setCurrentId(UUID.randomUUID().toString())
        _task.value = null
        _transcript.value = emptyList()
    }

    fun openConversation(conversationId: String) {
        if (isBusy()) return
        val id = conversationId.ifBlank { return }
        conversation.setCurrentId(id)
        scope.launch(dispatcher) {
            val rows = taskStore?.listByConversation(id).orEmpty()
            if (conversation.currentId() != id || isBusy()) return@launch
            _task.value = rows.lastOrNull()
            reloadTranscript()
        }
    }

    suspend fun reloadTranscript() {
        val store = taskStore
        if (store == null) {
            _transcript.value = emptyList()
            return
        }
        val liveId = _task.value?.taskId
        val id = conversation.currentId()
        _transcript.value = try {
            store.listByConversation(id).filter { entry ->
                isTerminal(entry.status) && entry.taskId != liveId
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
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

    private fun isBusy(): Boolean {
        val current = _task.value ?: return false
        return current.status != TaskStatus.COMPLETED && current.status != TaskStatus.FAILED
    }

    private suspend fun markCancelled() {
        val current = _task.value ?: return
        if (isTerminal(current.status)) return
        val failed = current.copy(
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.CANCELLED,
            streamingText = null,
        ).stampEndedAtIfTerminal()
        _task.value = failed
        persist(failed)
        reloadTranscript()
    }

    private suspend fun persist(task: AgentTask) {
        val store = taskStore ?: return
        try {
            store.upsert(task.stampEndedAtIfTerminal())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Skip persist if encode/store throws; the loop still runs.
        }
    }

    private fun isTerminal(status: TaskStatus): Boolean =
        status == TaskStatus.COMPLETED || status == TaskStatus.FAILED
}
