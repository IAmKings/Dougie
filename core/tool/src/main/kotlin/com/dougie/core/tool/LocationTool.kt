package com.dougie.core.tool

import com.dougie.core.model.AndroidPermissions
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolResult

class LocationTool(
    private val port: LocationPort,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = NAME,
        description = "Read a coarse device location as JSON (latitude, longitude, accuracy, provider).",
        riskLevel = RiskLevel.L1,
        androidPermission = AndroidPermissions.ACCESS_COARSE_LOCATION,
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        return ToolResult(json = port.lastKnownCoarse())
    }

    companion object {
        const val NAME = "location"
    }
}
