package com.dougie.cli

import com.dougie.core.llm.FakeLlmProvider
import com.dougie.core.runtime.LoopEngine
import com.dougie.core.runtime.TaskManager
import com.dougie.core.tool.FakeBatteryTool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal const val FAKE_BATTERY_PROMPT = "我现在手机还有多少电？"

internal fun fakeBatteryEngine(dispatcher: CoroutineDispatcher, stepDelayMs: Long): LoopEngine =
    LoopEngine(
        llm = FakeLlmProvider(),
        tools = mapOf("battery" to FakeBatteryTool()),
        dispatcher = dispatcher,
        stepDelayMs = stepDelayMs,
    )

internal fun fakeBatteryManager(
    dispatcher: CoroutineDispatcher,
    scope: CoroutineScope,
    stepDelayMs: Long,
): TaskManager = TaskManager(
    loopEngine = fakeBatteryEngine(dispatcher, stepDelayMs),
    dispatcher = dispatcher,
    scope = scope,
)
