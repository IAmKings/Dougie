package com.dougie.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmVendorsTest {
    @Test
    fun idForBaseUrlIgnoresTrailingSlash() {
        assertEquals("deepseek", LlmVendors.idForBaseUrl("https://api.deepseek.com/v1/"))
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
    }

    @Test
    fun clampAndParseMaxTokens() {
        assertEquals(16, LlmVendors.clampMaxTokens(1))
        assertEquals(8192, LlmVendors.clampMaxTokens(99_999))
        assertEquals(2048, LlmVendors.parseMaxTokens(" "))
        assertEquals(512, LlmVendors.parseMaxTokens("512"))
    }
}
