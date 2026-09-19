package com.dougie.app

import android.content.Context
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.IntentEval
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.IntentRuleEReport
import com.dougie.core.tool.OnnxIntentEngine
import com.dougie.tool.system.IntentOrtJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

fun adbPullHint(packageName: String): String =
    "adb exec-out run-as $packageName cat files/${IntentEval.PREDICTIONS_RELATIVE}"

fun formatRuleEMessage(packageName: String, report: IntentRuleEReport): String =
    report.toString() + "\n" + IntentEval.PREDICTIONS_RELATIVE + "\n" + adbPullHint(packageName)

object AppIntentRuleEEval {
    suspend fun run(context: Context): String = withContext(Dispatchers.Default) {
        try {
            val modelDir = File(context.filesDir, IntentModelLayout.DIR)
            if (!IntentModelLayout.isPresent(modelDir)) {
                return@withContext UserFacingErrors.INTENT_MODEL_MISSING
            }
            val engine = OnnxIntentEngine(
                modelDir = modelDir,
                nativeAvailable = IntentOrtJni::isAvailable,
                infer = { dir, features -> IntentOrtJni.infer(dir, features) },
                inferTokens = { dir, ids, mask -> IntentOrtJni.inferTokens(dir, ids, mask) },
            )
            if (!engine.isReady()) {
                return@withContext UserFacingErrors.INTENT_ENGINE_NOT_READY
            }
            val gold = IntentEval.loadHeldout()
            val (predictions, report) = IntentEval.runForward(engine, gold)
            val out = File(context.filesDir, IntentEval.PREDICTIONS_RELATIVE)
            IntentEval.writeJsonl(out, predictions)
            formatRuleEMessage(context.packageName, report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            e.userMessage
        } catch (_: Exception) {
            UserFacingErrors.INTENT_FAILED
        }
    }

    suspend fun last(context: Context): String? = withContext(Dispatchers.Default) {
        try {
            last(File(context.filesDir, IntentEval.PREDICTIONS_RELATIVE), context.packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun last(file: File, packageName: String): String? {
        if (!file.isFile) return null
        return try {
            val items = IntentEval.loadJsonl(file.readText())
            if (items.isEmpty()) return null
            formatRuleEMessage(packageName, IntentEval.ruleEReport(items))
        } catch (_: Exception) {
            null
        }
    }
}
