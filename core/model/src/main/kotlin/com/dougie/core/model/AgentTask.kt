package com.dougie.core.model

data class AgentTask(
    val taskId: String,
    val input: String,
    val status: TaskStatus = TaskStatus.IDLE,
    val loopCount: Int = 0,
    val maxLoops: Int = 8,
    val toolTrace: List<ToolTraceEntry> = emptyList(),
    val finalAnswer: String? = null,
    val lastError: String? = null,
    val streamingText: String? = null,
    val retrievedMemories: List<MemoryEntry> = emptyList(),
)

enum class TaskStatus {
    IDLE,
    PREPARING,
    THINKING,
    TOOL_PENDING,
    AWAITING_CONFIRMATION,
    TOOL_EXECUTING,
    TOOL_RESULT,
    COMPLETED,
    FAILED,
}

enum class RiskLevel {
    L0,
    L1,
    L2,
}

data class ToolTraceEntry(
    val toolCallId: String,
    val toolName: String,
    val argsSummary: String,
    val resultJson: String? = null,
    val status: ToolTraceStatus = ToolTraceStatus.PENDING,
    val riskLevel: RiskLevel = RiskLevel.L0,
)

enum class ToolTraceStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    FAILED,
}

data class LoopContext(
    val task: AgentTask,
)

sealed class LlmResponse {
    data class FinalAnswer(val text: String) : LlmResponse()
    data class ToolCall(
        val id: String,
        val name: String,
        val argsJson: String,
    ) : LlmResponse()
}

sealed class LlmEvent {
    data class TextDelta(val text: String) : LlmEvent()
    data class ToolCall(val id: String, val name: String, val argsJson: String) : LlmEvent()
}

data class ToolDescriptor(
    val name: String,
    val description: String = "",
    val properties: Map<String, ToolParamSpec> = emptyMap(),
    val riskLevel: RiskLevel = RiskLevel.L0,
    val androidPermission: String? = null,
)

object AndroidPermissions {
    const val READ_CALENDAR = "android.permission.READ_CALENDAR"
    const val WRITE_CALENDAR = "android.permission.WRITE_CALENDAR"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
}

data class ToolParamSpec(
    val type: ToolParamType,
    val defaultJson: String? = null,
)

enum class ToolParamType {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    OBJECT,
}

data class ToolContext(
    val taskId: String,
    val toolCallId: String,
) {
    val idempotencyKey: String get() = taskId + toolCallId
}

data class ToolResult(
    val json: String,
    val isFatal: Boolean = false,
    val error: String? = null,
)
