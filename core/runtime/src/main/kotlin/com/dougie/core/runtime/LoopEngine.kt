package com.dougie.core.runtime

import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.AgentException
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmResponse
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
    suspend fun run(initial: AgentTask, emit: suspend (AgentTask) -> Unit): AgentTask {
        return withContext(dispatcher) {
            var task = initial.copy(status = TaskStatus.PREPARING, lastError = null, finalAnswer = null)
            emit(task)
            stepDelay()

            while (task.loopCount < task.maxLoops) {
                task = task.copy(status = TaskStatus.THINKING)
                emit(task)
                stepDelay()

                val response = try {
                    withTimeout(llmTimeoutMs) {
                        gateway.complete(llm, LoopContext(task))
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

                when (response) {
                    is LlmResponse.FinalAnswer -> {
                        task = task.copy(status = TaskStatus.COMPLETED, finalAnswer = response.text)
                        emit(task)
                        return@withContext task
                    }

                    is LlmResponse.ToolCall -> {
                        val toolCallId = response.id.ifBlank { "call-${task.loopCount + 1}" }
                        val pending = ToolTraceEntry(
                            toolCallId = toolCallId,
                            toolName = response.name,
                            argsSummary = response.argsJson,
                            status = ToolTraceStatus.PENDING,
                        )
                        task = task.copy(
                            status = TaskStatus.TOOL_PENDING,
                            toolTrace = task.toolTrace + pending,
                        )
                        emit(task)
                        stepDelay()

                        task = updateLastTrace(task, TaskStatus.TOOL_EXECUTING) {
                            it.copy(status = ToolTraceStatus.EXECUTING)
                        }
                        emit(task)
                        stepDelay()

                        val tool = tools[response.name]
                        if (tool == null) {
                            return@withContext fail(task, "Unknown tool: ${response.name}", emit)
                        }

                        val result = try {
                            withTimeout(toolTimeoutMs) {
                                tool.execute(
                                    argumentsJson = response.argsJson,
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
                }
            }

            fail(task, "MaxLoopExceeded", emit)
        }
    }

    private suspend fun fail(
        task: AgentTask,
        message: String,
        emit: suspend (AgentTask) -> Unit,
    ): AgentTask {
        val failed = task.copy(status = TaskStatus.FAILED, lastError = message)
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
}
