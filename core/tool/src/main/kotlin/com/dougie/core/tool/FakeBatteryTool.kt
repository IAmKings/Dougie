package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolResult

class FakeBatteryTool : AgentTool {
    override val name: String = "battery"
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = name,
        description = "Read the device battery percent and charging state.",
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId) {
            "idempotencyKey must be taskId + toolCallId"
        }
        return ToolResult(json = STABLE_RESULT)
    }

    companion object {
        const val STABLE_RESULT = """{"battery_percent":63,"charging":true}"""
    }
}
