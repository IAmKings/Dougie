package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolResult

interface AgentTool {
    val name: String

    suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult
}
