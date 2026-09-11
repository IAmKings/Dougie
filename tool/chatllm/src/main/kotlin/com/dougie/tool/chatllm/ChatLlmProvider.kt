package com.dougie.tool.chatllm

import android.content.Context
import com.dougie.core.llm.ChatPromptAssembler
import com.dougie.core.llm.LlmProvider
import com.dougie.core.llm.LocalToolCallParser
import com.dougie.core.llm.toLlmResponse
import com.dougie.core.model.AgentException
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ChatModelLayout
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException

/** Sideload LiteRT-LM chat. Does not log prompt, completion, or weight paths. */
class ChatLlmProvider private constructor(
    private val filesDir: File,
    private val extraRoot: File?,
    private val cacheDir: File,
    private val toolDescriptors: () -> List<ToolDescriptor>,
    private val activeSku: () -> String?,
) : LlmProvider {
    override val isLocal: Boolean = true

    private val lock = Any()
    private var engine: Engine? = null
    private var loadedSku: String? = null
    private var loadedPath: String? = null
    private var inFlight = 0

    override fun stream(context: LoopContext): Flow<LlmEvent> = callbackFlow {
        val conversation: Conversation
        synchronized(lock) { inFlight += 1 }
        try {
            conversation = ChatLlmEngines.openConversation(ensureEngine())
        } catch (e: CancellationException) {
            synchronized(lock) { inFlight -= 1 }
            throw e
        } catch (e: AgentException) {
            synchronized(lock) { inFlight -= 1 }
            close(e)
            awaitClose { }
            return@callbackFlow
        } catch (_: Exception) {
            synchronized(lock) { inFlight -= 1 }
            close(AgentException(UserFacingErrors.CHAT_ENGINE_NOT_READY))
            awaitClose { }
            return@callbackFlow
        }
        val descriptors = toolDescriptors()
        conversation.sendMessageAsync(
            promptFor(context.task, descriptors),
            object : MessageCallback {
                private val buffer = StringBuilder()

                override fun onMessage(message: Message) {
                    val piece = message.toString()
                    if (piece.isEmpty()) return
                    buffer.append(piece)
                }

                override fun onDone() {
                    val text = buffer.toString()
                    val tool = LocalToolCallParser.parse(text)
                    when {
                        tool != null &&
                            LocalToolCallParser.isRepeatOfSuccessfulCall(context.task, tool) -> {
                            trySendBlocking(LlmEvent.TextDelta("已获得工具结果。"))
                        }
                        tool != null &&
                            ChatPromptAssembler.localToolProtocolActive(context.task, descriptors) -> {
                            trySendBlocking(tool)
                        }
                        text.isNotEmpty() -> trySendBlocking(
                            LlmEvent.TextDelta(
                                ChatPromptAssembler.stripLeadingQuestion(text, context.task.input),
                            ),
                        )
                    }
                    close()
                }

                override fun onError(throwable: Throwable) {
                    if (throwable is CancellationException) {
                        close(throwable)
                    } else {
                        close(AgentException(UserFacingErrors.LLM_FAILED))
                    }
                }
            },
            Collections.emptyMap<Any, Any>(),
            null,
            null,
            null,
            null,
            ThinkingConfig(false),
        )
        awaitClose {
            try {
                conversation.close()
            } catch (_: Exception) {
            }
            synchronized(lock) { inFlight -= 1 }
        }
    }.buffer(Channel.BUFFERED)

    override suspend fun generate(context: LoopContext): LlmResponse = stream(context).toLlmResponse()

    private fun ensureEngine(): Engine {
        synchronized(lock) {
            val chatDirs = ChatModelLayout.chatDirs(filesDir, extraRoot)
            val sku = ChatModelLayout.resolveActiveSku(activeSku(), chatDirs)
                ?: throw AgentException(UserFacingErrors.CHAT_MODEL_MISSING)
            val model = ChatModelLayout.locate(sku, chatDirs)
                ?: throw AgentException(UserFacingErrors.CHAT_MODEL_MISSING)
            val path = model.absolutePath
            engine?.let { current ->
                if (loadedSku == sku && loadedPath == path) return current
                if (inFlight > 1) return current
                releaseEngineLocked()
            }
            ChatLlmEngines.quietNativeLogs()
            val gpu = ChatLlmEngines.tryCreate(model, cacheDir, true)
            if (gpu != null) {
                engine = gpu
                loadedSku = sku
                loadedPath = path
                return gpu
            }
            val cpu = ChatLlmEngines.tryCreate(model, cacheDir, false)
                ?: throw AgentException(UserFacingErrors.CHAT_ENGINE_NOT_READY)
            engine = cpu
            loadedSku = sku
            loadedPath = path
            return cpu
        }
    }

    private fun releaseEngineLocked() {
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
        loadedSku = null
        loadedPath = null
    }

    companion object {
        @Volatile
        private var instance: ChatLlmProvider? = null

        fun get(
            context: Context,
            toolDescriptors: () -> List<ToolDescriptor> = { emptyList() },
            activeSku: () -> String? = { null },
        ): ChatLlmProvider {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: ChatLlmProvider(
                    filesDir = context.applicationContext.filesDir,
                    extraRoot = context.applicationContext.getExternalFilesDir(null),
                    cacheDir = context.applicationContext.cacheDir,
                    toolDescriptors = toolDescriptors,
                    activeSku = activeSku,
                ).also { instance = it }
            }
        }
    }
}

internal fun promptFor(
    task: AgentTask,
    descriptors: List<ToolDescriptor> = emptyList(),
): String = ChatPromptAssembler.localPrompt(task, descriptors)
