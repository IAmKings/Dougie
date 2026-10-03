package com.dougie.app

import android.content.ContextWrapper
import com.dougie.core.tool.InMemoryIdempotencyStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the verification-layer probe wiring: the SAM-conversion shape of the probe
 * parameters is exactly where the "non-null lambda on Play" regression came from, and
 * no core test can see this wiring.
 */
class ChannelToolsBridgeWiringTest {

    @Test
    fun registerWithConsentFillsBothVerificationBridges() {
        ForegroundProbeBridge.provider = null
        TreeProbeBridge.provider = null
        val tools = mutableMapOf<String, com.dougie.core.tool.AgentTool>()

        ChannelTools.register(
            tools,
            consentGranted = { true },
            idempotencyStore = InMemoryIdempotencyStore(),
            scriptPrivileged = { false },
            context = ContextWrapper(null),
        )

        assertNotNull(ForegroundProbeBridge.provider)
        assertNotNull(TreeProbeBridge.provider)
    }

    @Test
    fun registerWithoutConsentLeavesBridgesUnfilled() {
        ForegroundProbeBridge.provider = null
        TreeProbeBridge.provider = null
        val tools = mutableMapOf<String, com.dougie.core.tool.AgentTool>()

        ChannelTools.register(
            tools,
            consentGranted = { false },
            idempotencyStore = InMemoryIdempotencyStore(),
            scriptPrivileged = { false },
            context = ContextWrapper(null),
        )

        assertNull(ForegroundProbeBridge.provider)
        assertNull(TreeProbeBridge.provider)
    }
}
