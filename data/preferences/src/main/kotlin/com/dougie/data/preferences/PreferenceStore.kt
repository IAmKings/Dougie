package com.dougie.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.LlmVendors
import com.dougie.core.model.normalizeConversationTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class PreferenceStore(context: Context) {
    private val prefs: SharedPreferences
    private val _settings: MutableStateFlow<ProviderSettings>
    val settings: StateFlow<ProviderSettings>
    private val _openAppsJson: MutableStateFlow<String>
    val openAppsJson: StateFlow<String>
    private val _activeChatSku: MutableStateFlow<String>
    val activeChatSku: StateFlow<String>
    private val _conversationTitles: MutableStateFlow<Map<String, String>>
    val conversationTitles: StateFlow<Map<String, String>>

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
        _openAppsJson = MutableStateFlow(prefs.getString(KEY_OPEN_APPS, "") ?: "")
        openAppsJson = _openAppsJson.asStateFlow()
        _activeChatSku = MutableStateFlow(prefs.getString(KEY_ACTIVE_CHAT_SKU, "") ?: "")
        activeChatSku = _activeChatSku.asStateFlow()
        _conversationTitles = MutableStateFlow(readConversationTitles())
        conversationTitles = _conversationTitles.asStateFlow()
    }

    fun save(next: ProviderSettings) {
        val consent = when {
            next.allowCloud && next.egressConsentAt == null -> System.currentTimeMillis()
            else -> next.egressConsentAt
        }
        val stored = next.copy(
            egressConsentAt = consent,
            maxTokens = LlmVendors.clampMaxTokens(next.maxTokens),
        )
        prefs.edit()
            .putBoolean(KEY_ALLOW_CLOUD, stored.allowCloud)
            .putString(KEY_VENDOR_ID, stored.vendorId)
            .putString(KEY_BASE_URL, stored.baseUrl)
            .putString(KEY_MODEL, stored.model)
            .putString(KEY_API_KEY, stored.apiKey)
            .putInt(KEY_MAX_TOKENS, stored.maxTokens)
            .putBoolean(KEY_MEMORY_ENABLED, stored.memoryEnabled)
            .putString(KEY_MODEL_TREE_URI, stored.modelTreeUri)
            .putInt(KEY_TTS_SPEAKER_ID, stored.ttsSpeakerId)
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

    fun setModelTreeUri(uri: String) {
        save(settings.value.copy(modelTreeUri = uri))
    }

    fun setTtsSpeakerId(sid: Int) {
        save(settings.value.copy(ttsSpeakerId = sid))
    }

    fun setOpenAppsJson(json: String) {
        prefs.edit().putString(KEY_OPEN_APPS, json).apply()
        _openAppsJson.value = json
    }

    fun setActiveChatSku(skuId: String) {
        prefs.edit().putString(KEY_ACTIVE_CHAT_SKU, skuId).apply()
        _activeChatSku.value = skuId
    }

    fun currentConversationId(): String =
        prefs.getString(KEY_CURRENT_CONVERSATION, null)?.ifBlank { null }
            ?: ConversationIds.DEFAULT

    fun setCurrentConversationId(id: String) {
        val stored = id.ifBlank { ConversationIds.DEFAULT }
        prefs.edit().putString(KEY_CURRENT_CONVERSATION, stored).apply()
    }

    fun setConversationTitle(conversationId: String, raw: String) {
        val id = conversationId.ifBlank { return }
        val next = _conversationTitles.value.toMutableMap()
        val name = normalizeConversationTitle(raw)
        if (name == null) {
            next.remove(id)
        } else {
            next[id] = name
        }
        persistConversationTitles(next)
    }

    private fun readConversationTitles(): Map<String, String> {
        val raw = prefs.getString(KEY_CONVERSATION_TITLES, null)
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.isNullOrBlank()) continue
                    val name = normalizeConversationTitle(obj.optString(key, ""))
                    if (name != null) put(key, name)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistConversationTitles(map: Map<String, String>) {
        val editor = prefs.edit()
        if (map.isEmpty()) {
            editor.remove(KEY_CONVERSATION_TITLES)
        } else {
            val obj = JSONObject()
            map.forEach { (id, name) -> obj.put(id, name) }
            editor.putString(KEY_CONVERSATION_TITLES, obj.toString())
        }
        editor.apply()
        _conversationTitles.value = map
    }

    private fun read(): ProviderSettings {
        val consent = if (prefs.contains(KEY_CONSENT_AT)) prefs.getLong(KEY_CONSENT_AT, 0L) else null
        return ProviderSettings(
            allowCloud = prefs.getBoolean(KEY_ALLOW_CLOUD, false),
            vendorId = prefs.getString(KEY_VENDOR_ID, ProviderSettings.DEFAULT_VENDOR_ID)
                ?.ifBlank { null }
                ?: ProviderSettings.DEFAULT_VENDOR_ID,
            baseUrl = prefs.getString(KEY_BASE_URL, ProviderSettings.DEFAULT_BASE_URL)
                ?: ProviderSettings.DEFAULT_BASE_URL,
            model = prefs.getString(KEY_MODEL, ProviderSettings.DEFAULT_MODEL)
                ?: ProviderSettings.DEFAULT_MODEL,
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            maxTokens = LlmVendors.clampMaxTokens(
                prefs.getInt(KEY_MAX_TOKENS, ProviderSettings.DEFAULT_MAX_TOKENS),
            ),
            egressConsentAt = consent?.takeIf { it > 0L },
            memoryEnabled = prefs.getBoolean(KEY_MEMORY_ENABLED, true),
            modelTreeUri = prefs.getString(KEY_MODEL_TREE_URI, "").orEmpty(),
            ttsSpeakerId = prefs.getInt(KEY_TTS_SPEAKER_ID, 0),
        )
    }

    private companion object {
        const val PREFS_FILE = "dougie_provider_secure"
        const val KEY_ALLOW_CLOUD = "allow_cloud"
        const val KEY_VENDOR_ID = "vendor_id"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_CONSENT_AT = "egress_consent_at"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val KEY_MODEL_TREE_URI = "model_tree_uri"
        const val KEY_TTS_SPEAKER_ID = "tts_speaker_id"
        const val KEY_OPEN_APPS = "open_app_allowlist"
        const val KEY_ACTIVE_CHAT_SKU = "active_chat_sku"
        const val KEY_CURRENT_CONVERSATION = "current_conversation_id"
        const val KEY_CONVERSATION_TITLES = "conversation_titles_json"
    }
}
