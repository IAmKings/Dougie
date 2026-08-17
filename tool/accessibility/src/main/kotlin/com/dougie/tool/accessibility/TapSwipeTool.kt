package com.dougie.tool.accessibility

import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamSpec
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.ToolResult
import com.dougie.core.tool.AgentTool

class TapSwipeTool(
    private val consentGranted: () -> Boolean,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        if (!consentGranted()) {
            return ToolResult(
                json = """{"ok":false,"error":"$CONSENT_REQUIRED"}""",
                error = CONSENT_REQUIRED,
            )
        }
        return ToolResult(
            json = """{"ok":false,"error":"$NOT_ENABLED"}""",
            error = NOT_ENABLED,
        )
    }

    companion object {
        const val NAME = "tap_swipe"
        const val CONSENT_REQUIRED = "未完成侧载知情同意，无法执行屏幕操作"
        const val NOT_ENABLED = "侧载点击能力尚未启用"
        val DESCRIPTOR = ToolDescriptor(
            name = NAME,
            description = "Reserved screen tap/swipe. Not enabled in this build.",
            properties = mapOf(
                "x" to ToolParamSpec(ToolParamType.INTEGER, defaultJson = "0"),
                "y" to ToolParamSpec(ToolParamType.INTEGER, defaultJson = "0"),
            ),
            riskLevel = RiskLevel.L3,
        )
    }
}
