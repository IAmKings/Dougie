package com.dougie.app

import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.IdempotencyStore
import com.dougie.tool.accessibility.AndroidGesturePort
import com.dougie.tool.accessibility.TapSwipeTool

object ChannelTools {
    fun register(
        tools: MutableMap<String, AgentTool>,
        consentGranted: () -> Boolean,
        idempotencyStore: IdempotencyStore,
    ) {
        if (!consentGranted()) {
            tools.remove(TapSwipeTool.NAME)
            return
        }
        tools[TapSwipeTool.NAME] = TapSwipeTool(
            consentGranted = consentGranted,
            port = AndroidGesturePort(),
            idempotencyStore = idempotencyStore,
        )
    }
}
