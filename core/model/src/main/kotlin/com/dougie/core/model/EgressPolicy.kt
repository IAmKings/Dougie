package com.dougie.core.model

data class EgressPolicy(
    val allowCloud: Boolean = false,
)

data class CloudLlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val maxTokens: Int = LlmVendors.DEFAULT_MAX_TOKENS,
)
