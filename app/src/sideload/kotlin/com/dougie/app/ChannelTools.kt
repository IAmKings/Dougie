package com.dougie.app

import android.content.Context
import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.IdempotencyStore
import com.dougie.core.tool.JsEvalTool
import com.dougie.core.tool.PyEvalTool
import com.dougie.tool.accessibility.AndroidGesturePort
import com.dougie.tool.accessibility.TapSwipeTool
import com.dougie.tool.js.AndroidJsEvalPort
import com.dougie.tool.py.AndroidPyEvalPort

object ChannelTools {
    @Volatile
    private var pyPort: AndroidPyEvalPort? = null

    fun register(
        tools: MutableMap<String, AgentTool>,
        consentGranted: () -> Boolean,
        idempotencyStore: IdempotencyStore,
        scriptPrivileged: () -> Boolean = { false },
        context: Context,
    ) {
        tools[JsEvalTool.NAME] = JsEvalTool(AndroidJsEvalPort(), scriptPrivileged)
        if (scriptPrivileged()) {
            val port = pyPort ?: AndroidPyEvalPort(context.applicationContext).also { pyPort = it }
            tools[PyEvalTool.NAME] = PyEvalTool(port)
        } else {
            tools.remove(PyEvalTool.NAME)
        }
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
