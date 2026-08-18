package com.dougie.feature.chat

import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Test

class IntelligenceAvailableTest {

    @Test
    fun remoteConfiguredAndUsableIsSuper() {
        assertEquals(
            IntelligenceMark.SUPER,
            intelligenceMark(
                allowCloud = true,
                apiKeyConfigured = true,
                localLlmReady = false,
            ),
        )
        assertEquals(
            IntelligenceMark.SUPER,
            intelligenceMark(
                allowCloud = true,
                apiKeyConfigured = true,
                localLlmReady = true,
                failedLastError = UserFacingErrors.TOOL_FAILED,
            ),
        )
    }

    @Test
    fun noRemoteAndNoLocalIsNoob() {
        assertEquals(
            IntelligenceMark.NOOB,
            intelligenceMark(
                allowCloud = false,
                apiKeyConfigured = true,
                localLlmReady = false,
            ),
        )
        assertEquals(
            IntelligenceMark.NOOB,
            intelligenceMark(
                allowCloud = true,
                apiKeyConfigured = false,
                localLlmReady = false,
            ),
        )
    }

    @Test
    fun localChatLlmWithoutRemoteIsLogo() {
        assertEquals(
            IntelligenceMark.LOCAL,
            intelligenceMark(
                allowCloud = false,
                apiKeyConfigured = false,
                localLlmReady = true,
            ),
        )
    }

    @Test
    fun remoteCallFailureIsNoobEvenIfLocalReady() {
        for (error in listOf(
            UserFacingErrors.LLM_FAILED,
            UserFacingErrors.NETWORK_FAILED,
            UserFacingErrors.LLM_TIMEOUT,
        )) {
            assertEquals(
                IntelligenceMark.NOOB,
                intelligenceMark(
                    allowCloud = true,
                    apiKeyConfigured = true,
                    localLlmReady = true,
                    failedLastError = error,
                ),
            )
        }
    }

    @Test
    fun egressBlockedFallsBackToLocalLogo() {
        assertEquals(
            IntelligenceMark.LOCAL,
            intelligenceMark(
                allowCloud = true,
                apiKeyConfigured = true,
                localLlmReady = true,
                failedLastError = UserFacingErrors.EGRESS_BLOCKED,
            ),
        )
        assertEquals(
            IntelligenceMark.NOOB,
            intelligenceMark(
                allowCloud = true,
                apiKeyConfigured = true,
                localLlmReady = false,
                failedLastError = UserFacingErrors.EGRESS_BLOCKED,
            ),
        )
    }

    @Test
    fun missingApiKeyFallsBackToLocalLogo() {
        assertEquals(
            IntelligenceMark.LOCAL,
            intelligenceMark(
                allowCloud = true,
                apiKeyConfigured = true,
                localLlmReady = true,
                failedLastError = UserFacingErrors.MISSING_API_KEY,
            ),
        )
        assertEquals(
            IntelligenceMark.NOOB,
            intelligenceMark(
                allowCloud = true,
                apiKeyConfigured = true,
                localLlmReady = false,
                failedLastError = UserFacingErrors.MISSING_API_KEY,
            ),
        )
    }

    @Test
    fun intentGgufIsNotALocalChatLlm() {
        assertEquals(
            IntelligenceMark.NOOB,
            intelligenceMark(
                allowCloud = false,
                apiKeyConfigured = false,
                localLlmReady = false,
            ),
        )
        assertEquals(
            IntelligenceMark.SUPER,
            intelligenceMark(
                allowCloud = true,
                apiKeyConfigured = true,
                localLlmReady = false,
                failedLastError = UserFacingErrors.INTENT_MODEL_MISSING,
            ),
        )
    }
}
