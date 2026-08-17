package com.dougie.core.runtime

import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.AgentException
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LoopContext
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AgentTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class LoopEngine(
    private val llm: LlmProvider,
    private val tools: Map<String, AgentTool>,
    private val dispatcher: CoroutineDispatcher,
    private val stepDelayMs: Long = 280L,
    private val gateway: EgressGateway = EgressGateway(),
    private val llmTimeoutMs: Long = 60_000L,
    private val toolTimeoutMs: Long = 15_000L,
) {
    private val sanitizer = ToolCallSanitizer(tools.mapValues { it.value.descriptor })

    suspend fun run(initial: AgentTask, emit: suspend (AgentTask) -> Unit): AgentTask {
        return withContext(dispatcher) {
            var task = initial.copy(
                status = TaskStatus.PREPARING,
                lastError = null,
                finalAnswer = null,
                streamingText = null,
            )
            emit(task)
            stepDelay()

            while (task.loopCount < task.maxLoops) {
                task = task.copy(status = TaskStatus.THINKING, streamingText = null)
                emit(task)
                stepDelay()

                val turn: LlmTurn
                try {
                    turn = withTimeout(llmTimeoutMs) {
                        collectLlmTurn(task, emit)
                    }
                } catch (e: TimeoutCancellationException) {
                    return@withContext fail(task, UserFacingErrors.LLM_TIMEOUT, emit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AgentException) {
                    return@withContext fail(task, e.userMessage, emit)
                } catch (e: Exception) {
                    return@withContext fail(task, UserFacingErrors.LLM_FAILED, emit)
                }

                task = task.copy(streamingText = turn.streamingText)
                val toolEvent = turn.toolCall
                if (toolEvent == null) {
                    task = task.copy(
                        status = TaskStatus.COMPLETED,
                        finalAnswer = turn.streamingText.orEmpty(),
                        streamingText = null,
                    )
                    emit(task)
                    return@withContext task
                }

                val toolCallId = toolEvent.id.ifBlank { "call-${task.loopCount + 1}" }
                val pending = ToolTraceEntry(
                    toolCallId = toolCallId,
                    toolName = toolEvent.name,
                    argsSummary = toolEvent.argsJson,
                    status = ToolTraceStatus.PENDING,
                )
                task = task.copy(
                    status = TaskStatus.TOOL_PENDING,
                    streamingText = null,
                    toolTrace = task.toolTrace + pending,
                )
                emit(task)
                stepDelay()

                val sanitizedArgs = try {
                    sanitizer.sanitize(toolEvent.name, toolEvent.argsJson)
                } catch (e: AgentException) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    return@withContext fail(task, e.userMessage, emit)
                }

                task = updateLastTrace(task, TaskStatus.TOOL_PENDING) {
                    it.copy(argsSummary = sanitizedArgs)
                }

                task = updateLastTrace(task, TaskStatus.TOOL_EXECUTING) {
                    it.copy(status = ToolTraceStatus.EXECUTING)
                }
                emit(task)
                stepDelay()

                val tool = tools[toolEvent.name]
                if (tool == null) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    return@withContext fail(task, UserFacingErrors.UNKNOWN_TOOL, emit)
                }

                val result = try {
                    withTimeout(toolTimeoutMs) {
                        tool.execute(
                            argumentsJson = sanitizedArgs,
                            context = ToolContext(taskId = task.taskId, toolCallId = toolCallId),
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    return@withContext fail(task, UserFacingErrors.TOOL_TIMEOUT, emit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AgentException) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    return@withContext fail(task, e.userMessage, emit)
                } catch (e: Exception) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    return@withContext fail(task, UserFacingErrors.TOOL_FAILED, emit)
                }

                if (result.isFatal) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED, resultJson = result.json)
                    }.copy(lastError = result.error ?: UserFacingErrors.TOOL_FAILED)
                    emit(task)
                    return@withContext task
                }

                task = updateLastTrace(task, TaskStatus.TOOL_RESULT) {
                    it.copy(status = ToolTraceStatus.SUCCESS, resultJson = result.json)
                }
                emit(task)
                stepDelay()

                task = task.copy(loopCount = task.loopCount + 1)
                emit(task)
            }

            fail(task, "MaxLoopExceeded", emit)
        }
    }

    private suspend fun collectLlmTurn(
        start: AgentTask,
        emit: suspend (AgentTask) -> Unit,
    ): LlmTurn {
        var streamingText: String? = start.streamingText
        var toolEvent: LlmEvent.ToolCall? = null
        gateway.stream(llm, LoopContext(start)).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> {
                    streamingText = (streamingText ?: "") + event.text
                    emit(start.copy(streamingText = streamingText))
                }
                is LlmEvent.ToolCall -> toolEvent = event
            }
        }
        return LlmTurn(toolCall = toolEvent, streamingText = streamingText)
    }

    private suspend fun fail(
        task: AgentTask,
        message: String,
        emit: suspend (AgentTask) -> Unit,
    ): AgentTask {
        val failed = task.copy(status = TaskStatus.FAILED, lastError = message, streamingText = null)
        emit(failed)
        return failed
    }

    private fun updateLastTrace(
        task: AgentTask,
        status: TaskStatus,
        transform: (ToolTraceEntry) -> ToolTraceEntry,
    ): AgentTask {
        val trace = task.toolTrace.toMutableList()
        if (trace.isNotEmpty()) {
            trace[trace.lastIndex] = transform(trace.last())
        }
        return task.copy(status = status, toolTrace = trace)
    }

    private suspend fun stepDelay() {
        if (stepDelayMs > 0) delay(stepDelayMs)
    }

    private data class LlmTurn(
        val toolCall: LlmEvent.ToolCall?,
        val streamingText: String?,
    )
}
