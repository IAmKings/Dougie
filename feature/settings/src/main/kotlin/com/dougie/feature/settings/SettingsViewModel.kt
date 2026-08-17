package com.dougie.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dougie.data.preferences.PreferenceStore
import com.dougie.data.preferences.ProviderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsFormState(
    val allowCloud: Boolean = false,
    val baseUrl: String = ProviderSettings.DEFAULT_BASE_URL,
    val model: String = ProviderSettings.DEFAULT_MODEL,
    val apiKey: String = "",
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val store: PreferenceStore,
) : ViewModel() {
    private val _form = MutableStateFlow(store.settings.value.toForm())
    val form: StateFlow<SettingsFormState> = _form.asStateFlow()

    fun setAllowCloud(value: Boolean) {
        _form.update { it.copy(allowCloud = value, saved = false) }
    }

    fun setBaseUrl(value: String) {
        _form.update { it.copy(baseUrl = value, saved = false) }
    }

    fun setModel(value: String) {
        _form.update { it.copy(model = value, saved = false) }
    }

    fun setApiKey(value: String) {
        _form.update { it.copy(apiKey = value, saved = false) }
    }

    fun save() {
        val current = _form.value
        store.save(
            ProviderSettings(
                allowCloud = current.allowCloud,
                baseUrl = current.baseUrl.trim().ifBlank { ProviderSettings.DEFAULT_BASE_URL },
                model = current.model.trim().ifBlank { ProviderSettings.DEFAULT_MODEL },
                apiKey = current.apiKey.trim(),
                egressConsentAt = store.settings.value.egressConsentAt,
                memoryEnabled = store.settings.value.memoryEnabled,
            ),
        )
        _form.update { store.settings.value.toForm().copy(saved = true) }
    }

    class Factory(
        private val store: PreferenceStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(store) as T
        }
    }
}

private fun ProviderSettings.toForm() = SettingsFormState(
    allowCloud = allowCloud,
    baseUrl = baseUrl,
    model = model,
    apiKey = apiKey,
)
