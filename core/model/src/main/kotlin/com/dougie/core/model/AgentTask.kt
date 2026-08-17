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
)

enum class TaskStatus {
    IDLE,
    PREPARING,
    THINKING,
    TOOL_PENDING,
    TOOL_EXECUTING,
    TOOL_RESULT,
    COMPLETED,
    FAILED,
}

data class ToolTraceEntry(
    val toolCallId: String,
    val toolName: String,
    val argsSummary: String,
    val resultJson: String? = null,
    val status: ToolTraceStatus = ToolTraceStatus.PENDING,
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
