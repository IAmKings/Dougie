package com.dougie.app

import android.content.Context
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.KokoroEval
import com.dougie.core.tool.KokoroEvalLayout
import com.dougie.core.tool.KokoroEvalReport
import com.dougie.core.tool.ModelInstaller
import com.dougie.tool.system.SherpaJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

fun formatRuleBMessage(packageName: String, report: KokoroEvalReport): String =
    report.toString() + "\n" + KokoroEval.MANIFEST_RELATIVE + "\n" +
        adbPullHint(packageName, KokoroEval.MANIFEST_RELATIVE)

object AppKokoroRuleBEval {
    suspend fun run(
        context: Context,
        installer: ModelInstaller,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.Default) {
        try {
            val modelDir = File(context.filesDir, KokoroEvalLayout.DIR)
            if (!KokoroEvalLayout.isPresent(modelDir)) {
                KokoroEvalLayout.extractArchive(modelDir)
            }
            if (!KokoroEvalLayout.isPresent(modelDir)) {
                installer.install(
                    pack = KokoroEvalLayout.pack(),
                    destRoot = context.filesDir,
                    userConfirmed = true,
                    onProgress = onProgress,
                )
                KokoroEvalLayout.extractArchive(modelDir)
            }
            if (!KokoroEvalLayout.isPresent(modelDir)) {
                return@withContext UserFacingErrors.KOKORO_EVAL_MODEL_MISSING
            }
            onProgress(0L, -1L)
            val root = KokoroEvalLayout.resolvedDir(modelDir)
            SherpaJni.ensureKokoro(root)
            try {
                val gold = KokoroEval.loadGold()
                val (items, report) = KokoroEval.runForward(gold) { text ->
                    SherpaJni.generateKokoro(root, text)
                }
                val out = File(context.filesDir, KokoroEval.MANIFEST_RELATIVE)
                KokoroEval.writeJsonl(out, items)
                formatRuleBMessage(context.packageName, report)
            } finally {
                SherpaJni.releaseKokoro()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            e.userMessage
        } catch (_: Exception) {
            UserFacingErrors.KOKORO_EVAL_MODEL_MISSING
        }
    }

    suspend fun last(context: Context): String? = withContext(Dispatchers.Default) {
        try {
            last(File(context.filesDir, KokoroEval.MANIFEST_RELATIVE), context.packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun last(file: File, packageName: String): String? {
        if (!file.isFile) return null
        return try {
            val items = KokoroEval.loadJsonl(file.readText())
            if (items.isEmpty()) return null
            formatRuleBMessage(packageName, KokoroEval.report(items))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun markNaturalnessOk(context: Context): String = withContext(Dispatchers.Default) {
        try {
            markNaturalnessOk(File(context.filesDir, KokoroEval.MANIFEST_RELATIVE), context.packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            e.userMessage
        } catch (_: Exception) {
            UserFacingErrors.KOKORO_EVAL_MODEL_MISSING
        }
    }

    fun markNaturalnessOk(file: File, packageName: String): String {
        val existing = last(file, packageName) ?: return UserFacingErrors.KOKORO_EVAL_MODEL_MISSING
        return try {
            val items = KokoroEval.loadJsonl(file.readText())
            val report = KokoroEval.report(items)
            if (report.nScored < KokoroEval.MIN_N) return existing
            val marked = KokoroEval.markNaturalnessOk(items)
            KokoroEval.writeJsonl(file, marked)
            formatRuleBMessage(packageName, KokoroEval.report(marked))
        } catch (_: Exception) {
            existing
        }
    }
}
