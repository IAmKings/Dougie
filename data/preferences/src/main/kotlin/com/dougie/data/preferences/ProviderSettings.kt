package com.dougie.data.preferences

data class ProviderSettings(
    val allowCloud: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
    val egressConsentAt: Long? = null,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
