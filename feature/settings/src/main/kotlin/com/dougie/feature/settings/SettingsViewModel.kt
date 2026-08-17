package com.dougie.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.tool.ModelInstaller
import com.dougie.core.tool.OfflineModelOffer
import com.dougie.data.preferences.PreferenceStore
import com.dougie.data.preferences.ProviderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class SettingsFormState(
    val allowCloud: Boolean = false,
    val baseUrl: String = ProviderSettings.DEFAULT_BASE_URL,
    val model: String = ProviderSettings.DEFAULT_MODEL,
    val apiKey: String = "",
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val store: PreferenceStore,
    installer: ModelInstaller,
    destRoot: File,
    offers: List<OfflineModelOffer>,
) : ViewModel() {
    private val downloads = OfflineModelDownloads(installer, destRoot, offers, viewModelScope)
    private val _form = MutableStateFlow(store.settings.value.toForm())
    val form: StateFlow<SettingsFormState> = _form.asStateFlow()
    val models: StateFlow<OfflineModelsUi> = downloads.ui

    fun requestModel(id: String) = downloads.request(id)

    fun confirmModel() = downloads.confirm()

    fun dismissModelConfirm() = downloads.dismissConfirm()

    fun cancelModel(id: String) = downloads.cancel(id)

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
        private val installer: ModelInstaller,
        private val destRoot: File,
        private val offers: List<OfflineModelOffer>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(store, installer, destRoot, offers) as T
        }
    }
}

private fun ProviderSettings.toForm() = SettingsFormState(
    allowCloud = allowCloud,
    baseUrl = baseUrl,
    model = model,
    apiKey = apiKey,
)
