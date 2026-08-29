package com.dougie.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmVendorsTest {
    @Test
    fun idForBaseUrlIgnoresTrailingSlash() {
        assertEquals("deepseek", LlmVendors.idForBaseUrl("https://api.deepseek.com/v1/"))
        assertEquals("opencode-go", LlmVendors.idForBaseUrl("https://opencode.ai/zen/go/v1/"))
        assertEquals("openai", LlmVendors.idForBaseUrl("https://api.openai.com/v1"))
        assertEquals("siliconflow", LlmVendors.idForBaseUrl("https://api.siliconflow.cn/v1/"))
        assertEquals("groq", LlmVendors.idForBaseUrl("https://api.groq.com/openai/v1"))
        assertEquals("together", LlmVendors.idForBaseUrl("https://api.together.xyz/v1"))
        assertEquals("custom", LlmVendors.idForBaseUrl("https://example.com/v1"))
    }

    @Test
    fun resolvedVendorIdFollowsUrlWhenPresetNoLongerMatches() {
        assertEquals(
            "custom",
            LlmVendors.resolvedVendorId("openai", "https://example.com/v1"),
        )
        assertEquals(
            "moonshot",
            LlmVendors.resolvedVendorId("custom", "https://api.moonshot.cn/v1"),
        )
        assertEquals(
            "deepseek",
            LlmVendors.resolvedVendorId("deepseek", "https://api.deepseek.com/v1/"),
        )
        assertEquals(
            "opencode-go",
            LlmVendors.resolvedVendorId("custom", "https://opencode.ai/zen/go/v1/"),
        )
        assertEquals(
            "opencode-go",
            LlmVendors.resolvedVendorId("openai", "https://opencode.ai/zen/go/v1"),
        )
    }

    @Test
    fun openCodeGoAndDeepSeekFlashPresetsLeaveOpenAiAsInstallDefault() {
        assertEquals("openai", LlmVendors.OPENAI.id)
        assertEquals("gpt-4o-mini", LlmVendors.OPENAI.defaultModel)
        assertEquals("https://api.openai.com/v1", LlmVendors.OPENAI.baseUrl)
        assertEquals("deepseek-v4-flash", LlmVendors.DEEPSEEK.defaultModel)
        assertEquals("https://api.deepseek.com/v1", LlmVendors.DEEPSEEK.baseUrl)
        assertEquals("opencode-go", LlmVendors.OPENCODE_GO.id)
        assertEquals("OpenCode Go", LlmVendors.OPENCODE_GO.label)
        assertEquals("https://opencode.ai/zen/go/v1", LlmVendors.OPENCODE_GO.baseUrl)
        assertEquals("deepseek-v4-flash", LlmVendors.OPENCODE_GO.defaultModel)
        assertEquals(LlmVendors.V4_THINKING_MAX_TOKENS, LlmVendors.OPENCODE_GO.defaultMaxTokens)
        assertEquals(LlmVendors.V4_THINKING_MAX_TOKENS, LlmVendors.DEEPSEEK.defaultMaxTokens)
        assertEquals(LlmVendors.OPENAI, LlmVendors.ALL.first())
        assertEquals(1, LlmVendors.ALL.indexOf(LlmVendors.DEEPSEEK))
        assertEquals(2, LlmVendors.ALL.indexOf(LlmVendors.OPENCODE_GO))
        // ProviderSettings.DEFAULT_* bind to OPENAI in :data:preferences (unchanged this task).
    }

    @Test
    fun clampAndParseMaxTokens() {
        assertEquals(16, LlmVendors.clampMaxTokens(1))
        assertEquals(8192, LlmVendors.clampMaxTokens(99_999))
        assertEquals(2048, LlmVendors.parseMaxTokens(" "))
        assertEquals(512, LlmVendors.parseMaxTokens("512"))
        assertEquals(8192, LlmVendors.effectiveMaxTokens("deepseek-v4-flash", 2048))
        assertEquals(2048, LlmVendors.effectiveMaxTokens("gpt-4o-mini", 2048))
        assertEquals(8192, LlmVendors.effectiveMaxTokens("deepseek-v4-pro", 512))
    }
}
