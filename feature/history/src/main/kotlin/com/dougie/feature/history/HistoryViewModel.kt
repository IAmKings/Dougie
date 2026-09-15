package com.dougie.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.runtime.ConversationTitles
import com.dougie.core.runtime.TaskStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sections: List<HistorySection> = emptyList(),
)

class HistoryViewModel(
    private val taskStore: TaskStore,
    private val conversationTitles: ConversationTitles,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = HistoryUiState(
                sections = toHistorySections(
                    taskStore.listRecent(50).map { it.toHistoryItem() },
                    conversationTitles.titles(),
                ),
            )
        }
    }

    fun setTitle(conversationId: String, raw: String) {
        conversationTitles.setTitle(conversationId, raw)
        refresh()
    }

    class Factory(
        private val taskStore: TaskStore,
        private val conversationTitles: ConversationTitles,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(taskStore, conversationTitles) as T
        }
    }
}
