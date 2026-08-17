package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolResult
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SystemTimeTool(
    private val clock: Clock = Clock.systemDefaultZone(),
) : AgentTool {
    override val name: String = "time"
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = name,
        description = "Read the current local date and time.",
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId) {
            "idempotencyKey must be taskId + toolCallId"
        }
        val now = ZonedDateTime.now(clock)
        val iso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val json =
            """{"iso_local":"$iso","zone":"${now.zone}","epoch_ms":${now.toInstant().toEpochMilli()}}"""
        return ToolResult(json = json)
    }
}
