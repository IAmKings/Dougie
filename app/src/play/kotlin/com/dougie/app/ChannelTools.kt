package com.dougie.app

import android.content.Context
import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.IdempotencyStore

object ChannelTools {
    @Suppress("UNUSED_PARAMETER")
    fun register(
        tools: MutableMap<String, AgentTool>,
        consentGranted: () -> Boolean,
        idempotencyStore: IdempotencyStore,
        scriptPrivileged: () -> Boolean = { false },
        context: Context,
    ) {
        // Play must not register or compile accessibility / Python types.
    }
}
