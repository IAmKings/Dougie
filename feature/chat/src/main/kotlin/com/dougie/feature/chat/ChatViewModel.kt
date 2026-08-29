package com.dougie.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.runtime.TaskManager
import com.dougie.core.model.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ChatViewModel(
    private val taskManager: TaskManager,
) : ViewModel() {

    val uiState: StateFlow<ChatUiState> = taskManager.task
        .map { it.toChatUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun send(
        text: String,
        attachments: List<com.dougie.core.model.AttachmentMeta> = emptyList(),
        speakReply: Boolean = false,
    ) {
        val lastScreen = attachments.lastOrNull {
            it.kind == com.dougie.core.model.AttachmentKind.SCREEN
        }
        taskManager.submit(
            text,
            lastScreen?.id,
            lastScreen?.width,
            lastScreen?.height,
            attachments,
            speakReply,
        )
    }

    fun retry() {
        val current = taskManager.task.value ?: return
        if (current.status != TaskStatus.FAILED) return
        taskManager.submit(
            current.input,
            current.attachedCaptureId,
            current.attachedWidth,
            current.attachedHeight,
            current.attachments,
            current.speakReply,
        )
    }

    fun confirm() {
        taskManager.confirm()
    }

    fun reject() {
        taskManager.reject()
    }

    class Factory(
        private val taskManager: TaskManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(taskManager) as T
        }
    }
}
