package com.dougie.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.tool.OpenAppEntries
import com.dougie.core.tool.OpenAppEntry
import com.dougie.data.preferences.PreferenceStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LauncherApp(
    val label: String,
    val packageName: String,
)

class OpenAppsViewModel(
    private val store: PreferenceStore,
    private val listLaunchers: () -> List<LauncherApp>,
) : ViewModel() {
    val entries: StateFlow<List<OpenAppEntry>> = store.openAppsJson
        .map { OpenAppEntries.parse(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            OpenAppEntries.parse(store.openAppsJson.value),
        )

    fun launchers(): List<LauncherApp> = listLaunchers()

    fun add(label: String, packageName: String) {
        val next = OpenAppEntries.upsert(current(), label, packageName) ?: return
        store.setOpenAppsJson(OpenAppEntries.encode(next))
    }

    fun rename(packageName: String, alias: String) {
        val current = current()
        val row = current.firstOrNull { it.packageName == packageName } ?: return
        val next = OpenAppEntries.upsert(current, alias, row.packageName) ?: return
        store.setOpenAppsJson(OpenAppEntries.encode(next))
    }

    fun remove(packageName: String) {
        store.setOpenAppsJson(OpenAppEntries.encode(OpenAppEntries.remove(current(), packageName)))
    }

    private fun current(): List<OpenAppEntry> = OpenAppEntries.parse(store.openAppsJson.value)

    class Factory(
        private val store: PreferenceStore,
        private val listLaunchers: () -> List<LauncherApp>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OpenAppsViewModel(store, listLaunchers) as T
        }
    }
}
