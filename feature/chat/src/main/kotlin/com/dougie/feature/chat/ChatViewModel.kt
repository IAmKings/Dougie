package com.dougie.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.runtime.TaskManager
import com.dougie.core.model.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ChatViewModel(
    private val taskManager: TaskManager,
) : ViewModel() {

    val uiState: StateFlow<ChatUiState> = combine(taskManager.task, taskManager.transcript) { live, past ->
        mergeChatUiState(live, past)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    var feedIndex: Int = 0
        private set
    var feedOffset: Int = 0
        private set
    var feedItemCount: Int = 0
        private set
    var feedFirstKey: String? = null
        private set
    var feedLastAgent: String? = null
        private set

    private val _pendingFocusKey = MutableStateFlow<String?>(null)
    val pendingFocusKey: StateFlow<String?> = _pendingFocusKey.asStateFlow()

    fun requestFocus(taskId: String) {
        val id = taskId.trim()
        if (id.isEmpty()) return
        _pendingFocusKey.value = userMessageListKey(id)
    }

    fun clearPendingFocus() {
        _pendingFocusKey.value = null
    }

    fun saveFeedScroll(index: Int, offset: Int) {
        feedIndex = index
        feedOffset = offset
    }

    fun rememberFeedFollow(itemCount: Int, firstKey: String?, lastAgent: String?) {
        feedItemCount = itemCount
        feedFirstKey = firstKey
        feedLastAgent = lastAgent
    }

    private fun resetFeedFollow() {
        feedIndex = 0
        feedOffset = 0
        feedItemCount = 0
        feedFirstKey = null
        feedLastAgent = null
    }

    fun send(
        text: String,
        attachments: List<com.dougie.core.model.AttachmentMeta> = emptyList(),
        speakReply: Boolean = false,
    ) {
        clearPendingFocus()
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
        clearPendingFocus()
        taskManager.submit(
            current.input,
            current.attachedCaptureId,
            current.attachedWidth,
            current.attachedHeight,
            current.attachments,
            current.speakReply,
        )
    }

    fun newConversation() {
        clearPendingFocus()
        taskManager.newConversation()
        resetFeedFollow()
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
