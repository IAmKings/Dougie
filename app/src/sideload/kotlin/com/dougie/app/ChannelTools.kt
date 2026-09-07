package com.dougie.app

import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.IdempotencyStore
import com.dougie.core.tool.JsEvalTool
import com.dougie.tool.accessibility.AndroidGesturePort
import com.dougie.tool.accessibility.TapSwipeTool
import com.dougie.tool.js.AndroidJsEvalPort

object ChannelTools {
    fun register(
        tools: MutableMap<String, AgentTool>,
        consentGranted: () -> Boolean,
        idempotencyStore: IdempotencyStore,
    ) {
        tools[JsEvalTool.NAME] = JsEvalTool(AndroidJsEvalPort())
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
