package com.dougie.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceStore(context: Context) {
    private val prefs: SharedPreferences
    private val _settings: MutableStateFlow<ProviderSettings>
    val settings: StateFlow<ProviderSettings>

    init {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        _settings = MutableStateFlow(read())
        settings = _settings.asStateFlow()
    }

    fun save(next: ProviderSettings) {
        val consent = when {
            next.allowCloud && next.egressConsentAt == null -> System.currentTimeMillis()
            else -> next.egressConsentAt
        }
        val stored = next.copy(egressConsentAt = consent)
        prefs.edit()
            .putBoolean(KEY_ALLOW_CLOUD, stored.allowCloud)
            .putString(KEY_BASE_URL, stored.baseUrl)
            .putString(KEY_MODEL, stored.model)
            .putString(KEY_API_KEY, stored.apiKey)
            .putBoolean(KEY_MEMORY_ENABLED, stored.memoryEnabled)
            .apply {
                if (stored.egressConsentAt != null) {
                    putLong(KEY_CONSENT_AT, stored.egressConsentAt)
                } else {
                    remove(KEY_CONSENT_AT)
                }
            }
            .apply()
        _settings.value = stored
    }

    fun setMemoryEnabled(enabled: Boolean) {
        save(settings.value.copy(memoryEnabled = enabled))
    }

    private fun read(): ProviderSettings {
        val consent = if (prefs.contains(KEY_CONSENT_AT)) prefs.getLong(KEY_CONSENT_AT, 0L) else null
        return ProviderSettings(
            allowCloud = prefs.getBoolean(KEY_ALLOW_CLOUD, false),
            baseUrl = prefs.getString(KEY_BASE_URL, ProviderSettings.DEFAULT_BASE_URL)
                ?: ProviderSettings.DEFAULT_BASE_URL,
            model = prefs.getString(KEY_MODEL, ProviderSettings.DEFAULT_MODEL)
                ?: ProviderSettings.DEFAULT_MODEL,
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            egressConsentAt = consent?.takeIf { it > 0L },
            memoryEnabled = prefs.getBoolean(KEY_MEMORY_ENABLED, true),
        )
    }

    private companion object {
        const val PREFS_FILE = "dougie_provider_secure"
        const val KEY_ALLOW_CLOUD = "allow_cloud"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
        const val KEY_CONSENT_AT = "egress_consent_at"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
    }
}
