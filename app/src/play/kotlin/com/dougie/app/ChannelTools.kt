package com.dougie.app

import com.dougie.core.tool.AgentTool

object ChannelTools {
    @Suppress("UNUSED_PARAMETER")
    fun register(tools: MutableMap<String, AgentTool>, consentGranted: () -> Boolean) {
        // Play must not register or compile accessibility types.
    }
}
