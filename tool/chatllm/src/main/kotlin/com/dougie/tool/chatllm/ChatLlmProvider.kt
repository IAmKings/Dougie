package com.dougie.tool.chatllm

import android.content.Context
import com.dougie.core.llm.LlmProvider
import com.dougie.core.llm.toLlmResponse
import com.dougie.core.model.AgentException
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ChatModelLayout
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException

/** Sideload LiteRT-LM chat. Does not log prompt, completion, or weight paths. */
class ChatLlmProvider private constructor(
    private val filesDir: File,
    private val cacheDir: File,
) : LlmProvider {
    override val isLocal: Boolean = true

    private val lock = Any()
    private var engine: Engine? = null

    override fun stream(context: LoopContext): Flow<LlmEvent> = callbackFlow {
        val conversation: Conversation
        try {
            conversation = ChatLlmEngines.openConversation(ensureEngine())
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            close(e)
            awaitClose { }
            return@callbackFlow
        } catch (_: Exception) {
            close(AgentException(UserFacingErrors.CHAT_ENGINE_NOT_READY))
            awaitClose { }
            return@callbackFlow
        }
        conversation.sendMessageAsync(
            promptFor(context.task),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val piece = message.toString()
                    if (piece.isEmpty()) return
                    trySend(LlmEvent.TextDelta(piece))
                }

                override fun onDone() {
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
        }
    }

    override suspend fun generate(context: LoopContext): LlmResponse = stream(context).toLlmResponse()

    private fun ensureEngine(): Engine {
        synchronized(lock) {
            engine?.let { return it }
            if (!ChatModelLayout.isPresent(File(filesDir, ChatModelLayout.DIR))) {
                throw AgentException(UserFacingErrors.CHAT_MODEL_MISSING)
            }
            ChatLlmEngines.quietNativeLogs()
            val model = File(File(filesDir, ChatModelLayout.DIR), ChatModelLayout.MODEL_FILE)
            val gpu = ChatLlmEngines.tryCreate(model, cacheDir, true)
            if (gpu != null) {
                engine = gpu
                return gpu
            }
            val cpu = ChatLlmEngines.tryCreate(model, cacheDir, false)
                ?: throw AgentException(UserFacingErrors.CHAT_ENGINE_NOT_READY)
            engine = cpu
            return cpu
        }
    }

    companion object {
        @Volatile
        private var instance: ChatLlmProvider? = null

        fun get(context: Context): ChatLlmProvider {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: ChatLlmProvider(
                    filesDir = context.applicationContext.filesDir,
                    cacheDir = context.applicationContext.cacheDir,
                ).also { instance = it }
            }
        }
    }
}

internal fun promptFor(task: AgentTask): String {
    val traces = task.toolTrace.mapNotNull { trace ->
        val result = trace.resultJson ?: return@mapNotNull null
        "${trace.toolName}: $result"
    }
    if (traces.isEmpty()) return task.input
    return task.input + "\n" + traces.joinToString("\n")
}
