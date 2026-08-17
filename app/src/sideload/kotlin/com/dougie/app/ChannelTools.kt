package com.dougie.app

import com.dougie.core.tool.AgentTool
import com.dougie.tool.accessibility.TapSwipeTool

object ChannelTools {
    fun register(tools: MutableMap<String, AgentTool>, consentGranted: () -> Boolean) {
        if (!consentGranted()) {
            tools.remove(TapSwipeTool.NAME)
            return
        }
        tools[TapSwipeTool.NAME] = TapSwipeTool(consentGranted)
    }
}
