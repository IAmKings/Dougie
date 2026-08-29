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
) {
    private val _task = MutableStateFlow<AgentTask?>(null)
    val task: StateFlow<AgentTask?> = _task.asStateFlow()

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
        val current = _task.value
        if (current != null && current.status != TaskStatus.COMPLETED &&
            current.status != TaskStatus.FAILED
        ) {
            return
        }
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
        )
        _task.value = created
        running = scope.launch(dispatcher) {
            persist(created)
            try {
                loopEngine.run(created) { snapshot ->
                    _task.value = snapshot
                    persist(snapshot)
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

    fun confirm() {
        loopEngine.confirm()
    }

    fun reject() {
        loopEngine.reject()
    }

    fun cancel() {
        running?.cancel()
    }

    private suspend fun markCancelled() {
        val current = _task.value ?: return
        if (current.status == TaskStatus.COMPLETED || current.status == TaskStatus.FAILED) return
        val failed = current.copy(
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.CANCELLED,
            streamingText = null,
        )
        _task.value = failed
        persist(failed)
    }

    private suspend fun persist(task: AgentTask) {
        val store = taskStore ?: return
        try {
            store.upsert(task)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Skip persist if encode/store throws; the loop still runs.
        }
    }
}
