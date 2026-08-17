package com.dougie.core.llm

import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext

/**
 * Phase 0 script: any user input yields three battery ToolCalls, then a FinalAnswer.
 */
class FakeLlmProvider : LlmProvider {
    override val isLocal: Boolean = true

    override suspend fun generate(context: LoopContext): LlmResponse {
        val completed = context.task.toolTrace.count { it.resultJson != null }
        return if (completed < REQUIRED_TOOL_LOOPS) {
            LlmResponse.ToolCall(
                id = "battery-${completed + 1}",
                name = TOOL_NAME,
                argsJson = "{}",
            )
        } else {
            LlmResponse.FinalAnswer(buildFinalAnswer(context.task.toolTrace.mapNotNull { it.resultJson }))
        }
    }

    private fun buildFinalAnswer(results: List<String>): String {
        val percents = results.map { parseIntField(it, "battery_percent") ?: 63 }
        val charging = results.lastOrNull()?.let { parseBooleanField(it, "charging") } ?: true
        val chargingText = if (charging) "正在充电" else "没有在充电"
        return "你现在的手机电量是 ${percents.last()}%，目前$chargingText。" +
            "三次检测结果：${percents.joinToString(" / ")}%。"
    }

    private fun parseIntField(json: String, key: String): Int? {
        val match = Regex(""""$key"\s*:\s*(\d+)""").find(json) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun parseBooleanField(json: String, key: String): Boolean? {
        val match = Regex(""""$key"\s*:\s*(true|false)""").find(json) ?: return null
        return match.groupValues[1].toBooleanStrict()
    }

    companion object {
        const val REQUIRED_TOOL_LOOPS = 3
        const val TOOL_NAME = "battery"
    }
}
