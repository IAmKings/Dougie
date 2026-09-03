package com.dougie.core.runtime

import com.dougie.core.llm.LlmProvider
import com.dougie.core.memory.MemoryGate
import com.dougie.core.memory.MemoryStore
import com.dougie.core.model.AgentException
import com.dougie.core.model.AgentTask
import com.dougie.core.model.AttachmentLimits
import com.dougie.core.model.CompletionPath
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
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.IntentPort
import com.dougie.core.tool.OpenAppEntry
import com.dougie.core.tool.ScreenCaptureTool
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
    private val intentPort: IntentPort? = null,
    private val openAppEntries: () -> List<OpenAppEntry> = { emptyList() },
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

            val shortcut = completeFromIntentIfMatched(task, emit)
            if (shortcut != null) {
                return@withContext shortcut
            }

            while (task.loopCount < task.maxLoops) {
                task = task.copy(
                    status = TaskStatus.THINKING,
                    streamingText = null,
                    completionPath = CompletionPath.REMOTE_LLM,
                )
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
                when (
                    val pass = executeToolPass(
                        task = task,
                        toolName = toolEvent.name,
                        argsJson = toolEvent.argsJson,
                        toolCallId = toolCallId,
                        emit = emit,
                    )
                ) {
                    is ToolPass.Halt -> return@withContext pass.task
                    is ToolPass.Success -> {
                        task = pass.task.copy(loopCount = pass.task.loopCount + 1)
                        emit(task)
                    }
                }
            }

            fail(task, "MaxLoopExceeded", emit)
        }
    }

    private suspend fun completeFromIntentIfMatched(
        start: AgentTask,
        emit: suspend (AgentTask) -> Unit,
    ): AgentTask? {
        val port = intentPort ?: return null
        if (!start.attachedCaptureId.isNullOrBlank()) return null
        if (!port.isModelPresent() || !port.isEngineReady()) return null
        if (start.attachments.size >= AttachmentLimits.MAX) {
            val blocked = shortcutToolName(port, start.input)
            if (blocked == ScreenCaptureTool.NAME) {
                val routed = start.copy(completionPath = CompletionPath.LOCAL_INTENT)
                return fail(routed, UserFacingErrors.ATTACHMENTS_FULL, emit)
            }
            return null
        }
        if (start.attachments.isNotEmpty()) return null
        val toolName = shortcutToolName(port, start.input) ?: return null
        val entries = openAppEntries()
        val argsJson = IntentRouteAnswers.classifyTexts(start.input)
            .firstNotNullOfOrNull { IntentRouteAnswers.parseShortcutArgs(toolName, it, entries) }
            ?: return null
        val routed = start.copy(completionPath = CompletionPath.LOCAL_INTENT)
        return when (
            val pass = executeToolPass(
                task = routed,
                toolName = toolName,
                argsJson = argsJson,
                toolCallId = "intent-route-1",
                emit = emit,
            )
        ) {
            is ToolPass.Halt -> pass.task
            is ToolPass.Success -> {
                val answer = IntentRouteAnswers.formatFinalAnswer(toolName, pass.resultJson, entries)
                    ?: return fail(pass.task, UserFacingErrors.TOOL_FAILED, emit)
                val done = pass.task.copy(
                    status = TaskStatus.COMPLETED,
                    finalAnswer = answer,
                    streamingText = null,
                    loopCount = pass.task.loopCount + 1,
                )
                ingestMemory(done)
                emit(done)
                done
            }
        }
    }

    private suspend fun shortcutToolName(port: IntentPort, input: String): String? {
        for (text in IntentRouteAnswers.classifyTexts(input)) {
            val hit = try {
                port.classify(text)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            if (hit.confidence < IntentModelLayout.MIN_CONFIDENCE) continue
            val name = IntentRouteAnswers.toolNameFor(hit.intent)
            if (name != null) return name
        }
        return null
    }

    private suspend fun executeToolPass(
        task: AgentTask,
        toolName: String,
        argsJson: String,
        toolCallId: String,
        emit: suspend (AgentTask) -> Unit,
    ): ToolPass {
        val registered = tools[toolName]
        var next = task.copy(
            status = TaskStatus.TOOL_PENDING,
            streamingText = null,
            toolTrace = task.toolTrace + ToolTraceEntry(
                toolCallId = toolCallId,
                toolName = toolName,
                argsSummary = argsJson,
                status = ToolTraceStatus.PENDING,
                riskLevel = registered?.descriptor?.riskLevel ?: RiskLevel.L0,
            ),
        )
        emit(next)
        stepDelay()

        val sanitizedArgs = try {
            sanitizer.sanitize(toolName, argsJson)
        } catch (e: AgentException) {
            next = updateLastTrace(next, TaskStatus.FAILED) {
                it.copy(status = ToolTraceStatus.FAILED)
            }
            return ToolPass.Halt(fail(next, e.userMessage, emit))
        }

        next = updateLastTrace(next, TaskStatus.TOOL_PENDING) {
            it.copy(argsSummary = sanitizedArgs)
        }

        val tool = registered
        if (tool == null) {
            next = updateLastTrace(next, TaskStatus.FAILED) {
                it.copy(status = ToolTraceStatus.FAILED)
            }
            return ToolPass.Halt(fail(next, UserFacingErrors.UNKNOWN_TOOL, emit))
        }

        try {
            tool.validateArguments(sanitizedArgs)
        } catch (e: AgentException) {
            next = updateLastTrace(next, TaskStatus.FAILED) {
                it.copy(status = ToolTraceStatus.FAILED)
            }
            return ToolPass.Halt(fail(next, e.userMessage, emit))
        }

        when (policyEngine.decide(tool.descriptor)) {
            is PolicyDecision.DeniedPermission -> {
                next = updateLastTrace(next, TaskStatus.FAILED) {
                    it.copy(status = ToolTraceStatus.FAILED)
                }
                return ToolPass.Halt(fail(next, UserFacingErrors.PERMISSION_DENIED, emit))
            }
            PolicyDecision.NeedsConfirmation -> {
                val gate = CompletableDeferred<Boolean>()
                confirmGate = gate
                next = updateLastTrace(next, TaskStatus.AWAITING_CONFIRMATION) { it }
                emit(next)
                val confirmed = try {
                    withTimeout(confirmTimeoutMs) { gate.await() }
                } catch (e: TimeoutCancellationException) {
                    false
                } finally {
                    if (confirmGate === gate) confirmGate = null
                }
                if (!confirmed) {
                    next = updateLastTrace(next, TaskStatus.FAILED) {
                        it.copy(status = ToolTraceStatus.FAILED)
                    }
                    return ToolPass.Halt(fail(next, UserFacingErrors.CONFIRM_REJECTED, emit))
                }
            }
            PolicyDecision.Allow -> Unit
        }

        next = updateLastTrace(next, TaskStatus.TOOL_EXECUTING) {
            it.copy(status = ToolTraceStatus.EXECUTING)
        }
        emit(next)
        stepDelay()

        val result = try {
            withTimeout(toolTimeoutMs) {
                tool.execute(
                    argumentsJson = sanitizedArgs,
                    context = ToolContext(taskId = next.taskId, toolCallId = toolCallId),
                )
            }
        } catch (e: TimeoutCancellationException) {
            next = updateLastTrace(next, TaskStatus.FAILED) {
                it.copy(status = ToolTraceStatus.FAILED)
            }
            recordAudit(next.taskId, tool.name, "FAILED")
            return ToolPass.Halt(fail(next, UserFacingErrors.TOOL_TIMEOUT, emit))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            next = updateLastTrace(next, TaskStatus.FAILED) {
                it.copy(status = ToolTraceStatus.FAILED)
            }
            recordAudit(next.taskId, tool.name, "FAILED")
            return ToolPass.Halt(fail(next, e.userMessage, emit))
        } catch (e: Exception) {
            next = updateLastTrace(next, TaskStatus.FAILED) {
                it.copy(status = ToolTraceStatus.FAILED)
            }
            recordAudit(next.taskId, tool.name, "FAILED")
            return ToolPass.Halt(fail(next, UserFacingErrors.TOOL_FAILED, emit))
        }

        if (result.isFatal) {
            recordAudit(next.taskId, tool.name, "FAILED")
            next = updateLastTrace(next, TaskStatus.FAILED) {
                it.copy(status = ToolTraceStatus.FAILED, resultJson = result.json)
            }.copy(lastError = result.error ?: UserFacingErrors.TOOL_FAILED)
            emit(next)
            return ToolPass.Halt(next)
        }

        recordAudit(next.taskId, tool.name, "SUCCESS")
        next = updateLastTrace(next, TaskStatus.TOOL_RESULT) {
            it.copy(status = ToolTraceStatus.SUCCESS, resultJson = result.json)
        }
        emit(next)
        stepDelay()
        return ToolPass.Success(next, result.json)
    }

    private sealed class ToolPass {
        data class Success(val task: AgentTask, val resultJson: String) : ToolPass()
        data class Halt(val task: AgentTask) : ToolPass()
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
