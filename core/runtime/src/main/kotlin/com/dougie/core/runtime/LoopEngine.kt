package com.dougie.core.runtime

import com.dougie.core.llm.LlmProvider
import com.dougie.core.memory.MemoryGate
import com.dougie.core.memory.MemoryStore
import com.dougie.core.model.AgentException
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LoopContext
import com.dougie.core.model.MemoryEntry
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AgentTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
    private val memoryStore: MemoryStore? = null,
    private val memoryEnabled: () -> Boolean = { true },
    private val policyEngine: PolicyEngine = PolicyEngine(),
    private val confirmTimeoutMs: Long = 60_000L,
    private val auditLog: AuditLog = NoOpAuditLog,
) {
    private val sanitizer: ToolCallSanitizer
        get() = ToolCallSanitizer(tools.mapValues { it.value.descriptor })
    private val memoryGate = memoryStore?.let { MemoryGate(it, memoryEnabled) }

    @Volatile
    private var confirmGate: CompletableDeferred<Boolean>? = null

    fun confirm() {
        confirmGate?.complete(true)
    }

    fun reject() {
        confirmGate?.complete(false)
    }

    suspend fun run(initial: AgentTask, emit: suspend (AgentTask) -> Unit): AgentTask {
        return withContext(dispatcher) {
            var task = initial.copy(
                status = TaskStatus.PREPARING,
                lastError = null,
                finalAnswer = null,
                streamingText = null,
            )
            emit(task)
            task = retrieveMemories(task, emit)
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
                    val answer = turn.streamingText?.trim().orEmpty()
                    if (answer.isEmpty()) {
                        return@withContext fail(task, UserFacingErrors.LLM_EMPTY_REPLY, emit)
                    }
                    task = task.copy(
                        status = TaskStatus.COMPLETED,
                        finalAnswer = answer,
                        streamingText = null,
                    )
                    ingestMemory(task)
                    emit(task)
                    return@withContext task
                }

                val toolCallId = toolEvent.id.ifBlank { "call-${task.loopCount + 1}" }
                val registered = tools[toolEvent.name]
                val pending = ToolTraceEntry(
                    toolCallId = toolCallId,
                    toolName = toolEvent.name,
                    argsSummary = toolEvent.argsJson,
                    status = ToolTraceStatus.PENDING,
                    riskLevel = registered?.descriptor?.riskLevel ?: RiskLevel.L0,
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

                val tool = registered
                if (tool == null) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    return@withContext fail(task, UserFacingErrors.UNKNOWN_TOOL, emit)
                }

                try {
                    tool.validateArguments(sanitizedArgs)
                } catch (e: AgentException) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    return@withContext fail(task, e.userMessage, emit)
                }

                when (policyEngine.decide(tool.descriptor)) {
                    is PolicyDecision.DeniedPermission -> {
                        task = updateLastTrace(task, TaskStatus.FAILED) {
                            it.copy(status = ToolTraceStatus.FAILED)
                        }
                        return@withContext fail(task, UserFacingErrors.PERMISSION_DENIED, emit)
                    }
                    PolicyDecision.NeedsConfirmation -> {
                        val gate = CompletableDeferred<Boolean>()
                        confirmGate = gate
                        task = updateLastTrace(task, TaskStatus.AWAITING_CONFIRMATION) { it }
                        emit(task)
                        val confirmed = try {
                            withTimeout(confirmTimeoutMs) { gate.await() }
                        } catch (e: TimeoutCancellationException) {
                            false
                        } finally {
                            if (confirmGate === gate) confirmGate = null
                        }
                        if (!confirmed) {
                            task = updateLastTrace(task, TaskStatus.FAILED) {
                                it.copy(status = ToolTraceStatus.FAILED)
                            }
                            return@withContext fail(task, UserFacingErrors.CONFIRM_REJECTED, emit)
                        }
                    }
                    PolicyDecision.Allow -> Unit
                }

                task = updateLastTrace(task, TaskStatus.TOOL_EXECUTING) {
                    it.copy(status = ToolTraceStatus.EXECUTING)
                }
                emit(task)
                stepDelay()

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
                    recordAudit(task.taskId, tool.name, "FAILED")
                    return@withContext fail(task, UserFacingErrors.TOOL_TIMEOUT, emit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AgentException) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    recordAudit(task.taskId, tool.name, "FAILED")
                    return@withContext fail(task, e.userMessage, emit)
                } catch (e: Exception) {
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    recordAudit(task.taskId, tool.name, "FAILED")
                    return@withContext fail(task, UserFacingErrors.TOOL_FAILED, emit)
                }

                if (result.isFatal) {
                    recordAudit(task.taskId, tool.name, "FAILED")
                    task = updateLastTrace(task, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED, resultJson = result.json)
                    }.copy(lastError = result.error ?: UserFacingErrors.TOOL_FAILED)
                    emit(task)
                    return@withContext task
                }

                recordAudit(task.taskId, tool.name, "SUCCESS")
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

    private suspend fun retrieveMemories(
        task: AgentTask,
        emit: suspend (AgentTask) -> Unit,
    ): AgentTask {
        val store = memoryStore ?: return task
        if (!memoryEnabled()) return task
        val hits = try {
            budgetMemories(store.search(task.input, limit = MAX_MEMORY_FACTS))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return task
        }
        if (hits.isEmpty()) return task
        val next = task.copy(retrievedMemories = hits)
        emit(next)
        return next
    }

    private suspend fun ingestMemory(task: AgentTask) {
        val gate = memoryGate ?: return
        try {
            gate.ingest(task.input, task.finalAnswer, task.taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Loop still completes if Gate throws.
        }
    }

    private fun budgetMemories(entries: List<MemoryEntry>): List<MemoryEntry> {
        val out = ArrayList<MemoryEntry>(entries.size.coerceAtMost(MAX_MEMORY_FACTS))
        var chars = 0
        for (entry in entries.take(MAX_MEMORY_FACTS)) {
            val nextChars = chars + entry.content.length
            if (out.isNotEmpty() && nextChars > MAX_MEMORY_CHARS) break
            out += entry
            chars = nextChars
        }
        return out
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

    private fun recordAudit(taskId: String, toolName: String, outcome: String) {
        try {
            auditLog.record(taskId, toolName, outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Audit must never fail the loop.
        }
    }

    private suspend fun stepDelay() {
        if (stepDelayMs > 0) delay(stepDelayMs)
    }

    private data class LlmTurn(
        val toolCall: LlmEvent.ToolCall?,
        val streamingText: String?,
    )

    companion object {
        private const val MAX_MEMORY_FACTS = 5
        private const val MAX_MEMORY_CHARS = 800
    }
}
