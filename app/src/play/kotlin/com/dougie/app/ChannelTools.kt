package com.dougie.app

import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.IdempotencyStore

object ChannelTools {
    @Suppress("UNUSED_PARAMETER")
    fun register(
        tools: MutableMap<String, AgentTool>,
        consentGranted: () -> Boolean,
        idempotencyStore: IdempotencyStore,
    ) {
        // Play must not register or compile accessibility types.
    }
}
