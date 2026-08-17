package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolResult

interface AgentTool {
    val name: String
    val descriptor: ToolDescriptor
        get() = ToolDescriptor(name)

    suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult

    /** Schema/allowlist checks that must fail the task before Policy/Confirm. */
    fun validateArguments(argumentsJson: String) {}
}
