package com.dougie.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.model.TaskStatus
import com.dougie.core.runtime.ConversationTitles
import com.dougie.core.runtime.TaskStore
import com.dougie.core.runtime.conversationSearchNeedles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val query: String = "",
    val sections: List<HistorySection> = emptyList(),
)

class HistoryViewModel(
    private val taskStore: TaskStore,
    private val conversationTitles: ConversationTitles,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    private var loadGen = 0

    init {
        refresh()
    }

    fun setQuery(raw: String) {
        _uiState.value = _uiState.value.copy(query = raw)
        refresh()
    }

    fun refresh() {
        val gen = ++loadGen
        val query = _uiState.value.query
        viewModelScope.launch {
            val titles = conversationTitles.titles()
            val sections = loadSections(query, titles)
            if (gen != loadGen) return@launch
            _uiState.value = HistoryUiState(query = query, sections = sections)
        }
    }

    fun setTitle(conversationId: String, raw: String) {
        conversationTitles.setTitle(conversationId, raw)
        refresh()
    }

    private suspend fun loadSections(
        query: String,
        titles: Map<String, String>,
    ): List<HistorySection> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return toHistorySections(
                taskStore.listRecent(50).map { it.toHistoryItem() },
                titles,
            )
        }
        val needles = conversationSearchNeedles(trimmed)
        if (needles.isEmpty()) return emptyList()
        val storeHits = taskStore.searchHistory(trimmed)
        val recentSections = toHistorySections(
            taskStore.listRecent(50).map { it.toHistoryItem() },
            titles,
        )
        val titleIds = historyTitleHitIds(needles, recentSections, titles)
        val titleTasks = titleIds.associateWith { id ->
            taskStore.listByConversation(id)
                .filter { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }
                .asReversed()
        }
        val merged = mergeHistorySearchTasks(storeHits, titleTasks)
        return toHistorySections(merged.map { it.toHistoryItem() }, titles)
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
