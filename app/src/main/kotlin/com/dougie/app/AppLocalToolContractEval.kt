package com.dougie.app

import android.content.Context
import com.dougie.core.llm.LocalToolCallParser
import com.dougie.core.llm.LocalToolContractEval
import com.dougie.core.llm.LocalToolContractReport
import com.dougie.core.model.AgentException
import com.dougie.core.model.AgentTask
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ChatModelLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

const val LOCAL_TOOL_CONTRACT_SKU_HINT = "只认已启用的最小档对话模型。"

fun formatLocalToolContractMessage(packageName: String, report: LocalToolContractReport): String =
    report.toString() + "\n" + LocalToolContractEval.PREDICTIONS_RELATIVE + "\n" +
        adbPullHint(packageName, LocalToolContractEval.PREDICTIONS_RELATIVE)

object AppLocalToolContractEval {
    fun ready(context: Context): Boolean {
        val app = context.applicationContext as? DougieApplication ?: return false
        val sku = ChatModelLayout.resolveActiveSku(
            app.preferenceStore.activeChatSku.value,
            ChatModelLayout.chatDirs(app.filesDir, app.getExternalFilesDir(null)),
        )
        return sku == ChatModelLayout.ID
    }

    suspend fun run(context: Context): String = withContext(Dispatchers.Default) {
        try {
            val app = context.applicationContext as? DougieApplication
                ?: return@withContext UserFacingErrors.CHAT_ENGINE_NOT_READY
            if (!ready(app)) return@withContext LOCAL_TOOL_CONTRACT_SKU_HINT
            val provider = ChannelHooks.localChatProvider(app) { app.registeredToolDescriptors() }
                ?: return@withContext UserFacingErrors.CHAT_ENGINE_NOT_READY
            val items = LocalToolContractEval.loadResource()
            val predicted = LinkedHashMap<String, String?>()
            for (item in items) {
                coroutineContext.ensureActive()
                val response = provider.generate(
                    LoopContext(AgentTask(taskId = "contract-${item.id}", input = item.text)),
                )
                predicted[item.id] = when (response) {
                    is LlmResponse.ToolCall -> response.name.takeIf { it.isNotBlank() }
                    is LlmResponse.FinalAnswer -> LocalToolCallParser.parse(response.text)?.name
                }
            }
            val report = LocalToolContractEval.score(items, predicted)
            val out = File(app.filesDir, LocalToolContractEval.PREDICTIONS_RELATIVE)
            LocalToolContractEval.writePredictions(out, predicted)
            formatLocalToolContractMessage(app.packageName, report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            e.userMessage
        } catch (_: Exception) {
            UserFacingErrors.CHAT_ENGINE_NOT_READY
        }
    }

    suspend fun last(context: Context): String? = withContext(Dispatchers.Default) {
        try {
            val app = context.applicationContext
            last(File(app.filesDir, LocalToolContractEval.PREDICTIONS_RELATIVE), app.packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun last(file: File, packageName: String): String? {
        if (!file.isFile) return null
        return try {
            val predicted = LocalToolContractEval.loadPredictions(file.readText())
            if (predicted.isEmpty()) return null
            val report = LocalToolContractEval.score(LocalToolContractEval.loadResource(), predicted)
            formatLocalToolContractMessage(packageName, report)
        } catch (_: Exception) {
            null
        }
    }
}
