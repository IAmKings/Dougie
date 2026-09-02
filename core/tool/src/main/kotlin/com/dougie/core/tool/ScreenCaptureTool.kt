package com.dougie.core.tool

import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ScreenCaptureTool(
    private val port: ScreenCapturePort,
    private val store: ScreenFrameStore,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = NAME,
        description = "Capture the current screen. Returns capture_id, width, and height only. App must be in the foreground and MediaProjection must be granted.",
        riskLevel = RiskLevel.L1,
    )

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val pinned = store.pinned()
        if (pinned != null) {
            return metadata(pinned)
        }
        if (!port.isAppForeground()) {
            return ToolResult(
                json = failJson(UserFacingErrors.SCREEN_NOT_FOREGROUND),
                isFatal = true,
                error = UserFacingErrors.SCREEN_NOT_FOREGROUND,
            )
        }
        if (!port.hasProjectionConsent()) {
            return ToolResult(
                json = failJson(UserFacingErrors.PERMISSION_DENIED),
                isFatal = true,
                error = UserFacingErrors.PERMISSION_DENIED,
            )
        }
        val captured = port.capture()
        store.put(captured.frame)
        return metadata(captured.frame)
    }

    private fun metadata(frame: ScreenFrame): ToolResult = ToolResult(
        json = buildJsonObject {
            put("capture_id", frame.id)
            put("width", frame.width)
            put("height", frame.height)
        }.toString(),
    )

    private fun failJson(message: String): String = buildJsonObject {
        put("ok", false)
        put("error", message)
    }.toString()

    companion object {
        const val NAME = "screen_capture"
    }
}
