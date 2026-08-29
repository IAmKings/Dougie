package com.dougie.data.preferences

import com.dougie.core.model.LlmVendors

data class ProviderSettings(
    val allowCloud: Boolean = false,
    val vendorId: String = DEFAULT_VENDOR_ID,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val egressConsentAt: Long? = null,
    val memoryEnabled: Boolean = true,
    val modelTreeUri: String = "",
    val ttsSpeakerId: Int = 0,
) {
    companion object {
        val DEFAULT_VENDOR_ID = LlmVendors.OPENAI.id
        val DEFAULT_BASE_URL = LlmVendors.OPENAI.baseUrl
        val DEFAULT_MODEL = LlmVendors.OPENAI.defaultModel
        const val DEFAULT_MAX_TOKENS = LlmVendors.DEFAULT_MAX_TOKENS
    }
}
