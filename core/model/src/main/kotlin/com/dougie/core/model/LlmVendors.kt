package com.dougie.core.model

data class LlmVendorPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val defaultMaxTokens: Int,
)

object LlmVendors {
    const val CUSTOM_ID = "custom"
    const val DEFAULT_MAX_TOKENS = 2048
    const val MIN_MAX_TOKENS = 16
    const val MAX_MAX_TOKENS = 8192

    val OPENAI = LlmVendorPreset(
        id = "openai",
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        defaultMaxTokens = DEFAULT_MAX_TOKENS,
    )
    val DEEPSEEK = LlmVendorPreset(
        id = "deepseek",
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        defaultMaxTokens = DEFAULT_MAX_TOKENS,
    )
    val MOONSHOT = LlmVendorPreset(
        id = "moonshot",
        label = "月之暗面 Kimi",
        baseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "moonshot-v1-8k",
        defaultMaxTokens = DEFAULT_MAX_TOKENS,
    )
    val SILICONFLOW = LlmVendorPreset(
        id = "siliconflow",
        label = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "deepseek-ai/DeepSeek-V3",
        defaultMaxTokens = DEFAULT_MAX_TOKENS,
    )
    val GROQ = LlmVendorPreset(
        id = "groq",
        label = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        defaultMaxTokens = DEFAULT_MAX_TOKENS,
    )
    val TOGETHER = LlmVendorPreset(
        id = "together",
        label = "Together AI",
        baseUrl = "https://api.together.xyz/v1",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        defaultMaxTokens = DEFAULT_MAX_TOKENS,
    )
    val CUSTOM = LlmVendorPreset(
        id = CUSTOM_ID,
        label = "自定义",
        baseUrl = "",
        defaultModel = "",
        defaultMaxTokens = DEFAULT_MAX_TOKENS,
    )

    val ALL: List<LlmVendorPreset> = listOf(
        OPENAI,
        DEEPSEEK,
        MOONSHOT,
        SILICONFLOW,
        GROQ,
        TOGETHER,
        CUSTOM,
    )

    fun byId(id: String): LlmVendorPreset {
        return ALL.firstOrNull { it.id == id } ?: CUSTOM
    }

    fun resolvedVendorId(storedId: String, baseUrl: String): String {
        val fromUrl = idForBaseUrl(baseUrl)
        val stored = byId(storedId)
        if (stored.id == CUSTOM_ID) return fromUrl
        if (normalizeBaseUrl(stored.baseUrl) == normalizeBaseUrl(baseUrl)) return stored.id
        return fromUrl
    }

    fun idForBaseUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        if (normalized.isEmpty()) return CUSTOM_ID
        return ALL.firstOrNull { it.id != CUSTOM_ID && normalizeBaseUrl(it.baseUrl) == normalized }
            ?.id
            ?: CUSTOM_ID
    }

    fun clampMaxTokens(value: Int): Int = value.coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS)

    fun parseMaxTokens(raw: String): Int {
        return clampMaxTokens(raw.trim().toIntOrNull() ?: DEFAULT_MAX_TOKENS)
    }

    fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')
}
