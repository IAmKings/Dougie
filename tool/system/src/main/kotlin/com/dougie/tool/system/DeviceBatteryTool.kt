package com.dougie.tool.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolResult
import com.dougie.core.tool.AgentTool

class DeviceBatteryTool(
    private val appContext: Context,
) : AgentTool {
    override val name: String = "battery"
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = name,
        description = "Read the device battery percent and charging state.",
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId) {
            "idempotencyKey must be taskId + toolCallId"
        }
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return ToolResult(json = """{"battery_percent":$percent,"charging":$charging}""")
    }
}
