package com.dougie.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.memory.MemoryStore
import com.dougie.core.model.MemoryEntry
import com.dougie.data.preferences.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemoryUiState(
    val entries: List<MemoryEntry> = emptyList(),
    val memoryEnabled: Boolean = true,
)

class MemoryViewModel(
    private val store: MemoryStore,
    private val preferences: PreferenceStore,
) : ViewModel() {
    private val entries = MutableStateFlow<List<MemoryEntry>>(emptyList())

    val uiState: StateFlow<MemoryUiState> = combine(
        entries,
        preferences.settings.map { it.memoryEnabled },
    ) { list, enabled -> MemoryUiState(entries = list, memoryEnabled = enabled) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MemoryUiState(memoryEnabled = preferences.settings.value.memoryEnabled),
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            entries.value = store.list()
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        preferences.setMemoryEnabled(enabled)
    }

    fun delete(id: String) {
        viewModelScope.launch {
            store.delete(id)
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            store.clear()
            refresh()
        }
    }

    fun updateContent(entry: MemoryEntry, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            store.upsert(entry.copy(content = trimmed, updatedAt = System.currentTimeMillis()))
            refresh()
        }
    }

    class Factory(
        private val store: MemoryStore,
        private val preferences: PreferenceStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MemoryViewModel(store, preferences) as T
        }
    }
}
