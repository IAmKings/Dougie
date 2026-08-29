package com.dougie.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.model.LlmVendors
import com.dougie.core.tool.ModelImporter
import com.dougie.core.tool.ModelInstaller
import com.dougie.core.tool.OfflineModelOffer
import com.dougie.data.preferences.PreferenceStore
import com.dougie.data.preferences.ProviderSettings
import com.dougie.core.tool.TtsVoices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File

data class SettingsFormState(
    val allowCloud: Boolean = false,
    val vendorId: String = ProviderSettings.DEFAULT_VENDOR_ID,
    val baseUrl: String = ProviderSettings.DEFAULT_BASE_URL,
    val model: String = ProviderSettings.DEFAULT_MODEL,
    val apiKey: String = "",
    val maxTokensText: String = ProviderSettings.DEFAULT_MAX_TOKENS.toString(),
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val store: PreferenceStore,
    installer: ModelInstaller,
    destRoot: File,
    cacheRoot: File,
    offers: List<OfflineModelOffer>,
    importer: ModelImporter = ModelImporter(),
    probe: OfflineModelProbe = OfflineModelProbe {
        ProbeResult(ok = false, message = "离线模型测试尚未接入。")
    },
    tree: ExternalModelTree = NoExternalModelTree,
) : ViewModel() {
    private val downloads = OfflineModelDownloads(
        installer = installer,
        destRoot = destRoot,
        cacheRoot = cacheRoot,
        offers = offers,
        scope = viewModelScope,
        importer = importer,
        probe = probe,
        tree = tree,
    )
    private val _form = MutableStateFlow(store.settings.value.toForm())
    val form: StateFlow<SettingsFormState> = _form.asStateFlow()
    val models: StateFlow<OfflineModelsUi> = downloads.ui
    val ttsSpeakerId: StateFlow<Int> = store.settings
        .map { TtsVoices.clamp(it.ttsSpeakerId) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TtsVoices.clamp(store.settings.value.ttsSpeakerId),
        )

    fun requestModel(id: String) = downloads.request(id)

    fun confirmModel() = downloads.confirm()

    fun dismissModelConfirm() = downloads.dismissConfirm()

    fun probeModel(id: String) = downloads.probe(id)

    fun cancelModel(id: String) = downloads.cancel(id)

    fun scanModels() = downloads.scan()

    fun setModelTreeUri(uri: String) {
        store.setModelTreeUri(uri)
        downloads.scan()
    }

    fun setTtsSpeakerId(sid: Int) {
        store.setTtsSpeakerId(TtsVoices.clamp(sid))
    }

    fun setAllowCloud(value: Boolean) {
        _form.update { it.copy(allowCloud = value, saved = false) }
    }

    fun setVendor(id: String) {
        val preset = LlmVendors.byId(id)
        _form.update { current ->
            if (preset.id == LlmVendors.CUSTOM_ID) {
                current.copy(vendorId = LlmVendors.CUSTOM_ID, saved = false)
            } else {
                current.copy(
                    vendorId = preset.id,
                    baseUrl = preset.baseUrl,
                    model = preset.defaultModel,
                    maxTokensText = preset.defaultMaxTokens.toString(),
                    saved = false,
                )
            }
        }
    }

    fun setBaseUrl(value: String) {
        _form.update {
            it.copy(
                baseUrl = value,
                vendorId = LlmVendors.resolvedVendorId(it.vendorId, value),
                saved = false,
            )
        }
    }

    fun setModel(value: String) {
        _form.update { it.copy(model = value, saved = false) }
    }

    fun setApiKey(value: String) {
        _form.update { it.copy(apiKey = value, saved = false) }
    }

    fun setMaxTokensText(value: String) {
        _form.update {
            it.copy(
                maxTokensText = value.filter { ch -> ch.isDigit() }.take(6),
                saved = false,
            )
        }
    }

    fun save() {
        val current = _form.value
        val baseUrl = current.baseUrl.trim().ifBlank { ProviderSettings.DEFAULT_BASE_URL }
        store.save(
            ProviderSettings(
                allowCloud = current.allowCloud,
                vendorId = LlmVendors.resolvedVendorId(current.vendorId, baseUrl),
                baseUrl = baseUrl,
                model = current.model.trim().ifBlank { ProviderSettings.DEFAULT_MODEL },
                apiKey = current.apiKey.trim(),
                maxTokens = LlmVendors.parseMaxTokens(current.maxTokensText),
                egressConsentAt = store.settings.value.egressConsentAt,
                memoryEnabled = store.settings.value.memoryEnabled,
                modelTreeUri = store.settings.value.modelTreeUri,
                ttsSpeakerId = store.settings.value.ttsSpeakerId,
            ),
        )
        _form.update { store.settings.value.toForm().copy(saved = true) }
    }

    class Factory(
        private val store: PreferenceStore,
        private val installer: ModelInstaller,
        private val destRoot: File,
        private val cacheRoot: File,
        private val offers: List<OfflineModelOffer>,
        private val importer: ModelImporter = ModelImporter(),
        private val probe: OfflineModelProbe,
        private val tree: ExternalModelTree,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                store,
                installer,
                destRoot,
                cacheRoot,
                offers,
                importer,
                probe,
                tree,
            ) as T
        }
    }
}

private fun ProviderSettings.toForm() = SettingsFormState(
    allowCloud = allowCloud,
    vendorId = LlmVendors.resolvedVendorId(vendorId, baseUrl),
    baseUrl = baseUrl,
    model = model,
    apiKey = apiKey,
    maxTokensText = LlmVendors.clampMaxTokens(maxTokens).toString(),
)
