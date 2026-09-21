package com.dougie.feature.debug

import com.dougie.core.model.AgentTask
import com.dougie.core.model.CompletionPath
import com.dougie.core.model.ConversationHit
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.runtime.AuditEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugUiStateTest {
    @Test
    fun mapsTaskIdStatusLoopCountAndLastErrorOnly() {
        val snapshot = AgentTask(
            taskId = "t1",
            input = "secret user prompt",
            status = TaskStatus.FAILED,
            loopCount = 3,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "c1",
                    toolName = "battery",
                    argsSummary = "{\"hidden\":true}",
                    resultJson = "{\"battery_percent\":63}",
                ),
            ),
            lastError = "任务失败",
            completionPath = CompletionPath.REMOTE_LLM,
        ).toDebugTaskSnapshot()
        assertEquals("t1", snapshot.taskId)
        assertEquals("FAILED", snapshot.status)
        assertEquals(3, snapshot.loopCount)
        assertEquals("任务失败", snapshot.lastError)
        assertEquals("远程 LLM", snapshot.completionPath)
        val dumped = snapshot.toString()
        assertFalse(dumped.contains("secret user prompt"))
        assertFalse(dumped.contains("battery_percent"))
        assertFalse(dumped.contains("hidden"))
        assertFalse(dumped.contains("query_time"))
    }

    @Test
    fun mapsLocalIntentPathAndNullAsNone() {
        val local = AgentTask(
            taskId = "t-local",
            input = "现在几点",
            status = TaskStatus.COMPLETED,
            completionPath = CompletionPath.LOCAL_INTENT,
        ).toDebugTaskSnapshot()
        assertEquals("本地意图", local.completionPath)
        assertFalse(local.toString().contains("现在几点"))
        val none = AgentTask(taskId = "t0", input = "x").toDebugTaskSnapshot()
        assertEquals("无", none.completionPath)
    }

    @Test
    fun mapsLocalLlmPath() {
        val snapshot = AgentTask(
            taskId = "t-local-llm",
            input = "你是谁",
            status = TaskStatus.COMPLETED,
            completionPath = CompletionPath.LOCAL_LLM,
        ).toDebugTaskSnapshot()
        assertEquals("本地 LLM", snapshot.completionPath)
        assertFalse(snapshot.toString().contains("你是谁"))
    }

    @Test
    fun debugSnapshotOmitsConversationHitBodies() {
        val snapshot = AgentTask(
            taskId = "t-hist",
            input = "UNO 项目有哪些关键点？",
            status = TaskStatus.COMPLETED,
            retrievedConversationHits = listOf(
                ConversationHit(
                    taskId = "old-uno",
                    conversationId = "window-a",
                    sourceLabel = "工作 · UNO 项目关键点",
                    user = "HIT_USER_SECRET_XYZ",
                    assistant = "HIT_ASSISTANT_SECRET_XYZ",
                ),
            ),
        ).toDebugTaskSnapshot()
        val dumped = snapshot.toString()
        assertFalse(dumped.contains("HIT_USER_SECRET_XYZ"))
        assertFalse(dumped.contains("HIT_ASSISTANT_SECRET_XYZ"))
        assertFalse(dumped.contains("工作 · UNO 项目关键点"))
        assertFalse(dumped.contains("UNO 项目有哪些关键点？"))
    }

    @Test
    fun mapsAuditEntryWithoutArgs() {
        val row = AuditEntry(
            taskId = "t2",
            toolName = "time",
            outcome = "SUCCESS",
            createdAt = 1L,
        ).toDebugAuditRow()
        assertEquals("t2", row.taskId)
        assertEquals("time", row.toolName)
        assertEquals("SUCCESS", row.outcome)
        assertEquals(1L, row.createdAt)
    }

    @Test
    fun uiModelsDoNotDeclareResultJsonOrPrompt() {
        val names = listOf(
            DebugTaskSnapshot::class.java,
            DebugAuditRow::class.java,
            DebugUiState::class.java,
        ).flatMap { type -> type.declaredFields.map { it.name } }
        assertFalse(names.any { it.contains("resultJson", ignoreCase = true) })
        assertFalse(names.any { it.contains("prompt", ignoreCase = true) })
        assertFalse(names.any { it.contains("args", ignoreCase = true) })
        assertFalse(names.any { it.contains("streaming", ignoreCase = true) })
        assertFalse(names.any { it.contains("finalAnswer", ignoreCase = true) })
        assertFalse(names.any { it.contains("toolTrace", ignoreCase = true) })
        assertFalse(names.any { it.contains("retrievedConversation", ignoreCase = true) })
        assertFalse(names.any { it.contains("sourceLabel", ignoreCase = true) })
        assertNull(DebugTaskSnapshot::class.java.declaredFields.find { it.name == "input" })
        val fields = DebugUiState::class.java.declaredFields.map { it.name }
        assertTrue(fields.any { it == "ruleEMessage" })
        assertTrue(fields.any { it == "ruleEBusy" })
        assertTrue(fields.any { it == "ruleBMessage" })
        assertTrue(fields.any { it == "ruleBBusy" })
        assertFalse(names.any { it.contains("utterance", ignoreCase = true) })
        assertFalse(names.any { it.contains("pcm", ignoreCase = true) })
    }

    @Test
    fun ruleECopyNeverClaimsPassed() {
        assertEquals("评测意图规则 E", RULE_E_ACTION_LABEL)
        assertFalse(RULE_E_ACTION_LABEL.contains("已达标"))
        val state = DebugUiState(
            ruleEBusy = false,
            ruleEMessage = "intent nLabeled=88 nScored=88 nUnscored=0 nClasses=11 " +
                "accuracy=1.0 p95Ms=10 latencyApplied=true ruleEPassed=true\n" +
                "eval/intent/predictions.jsonl\n" +
                "adb exec-out run-as com.dougie.app cat files/eval/intent/predictions.jsonl",
        )
        assertFalse(state.toString().contains("已达标"))
        assertFalse(state.ruleEMessage!!.contains("已达标"))
        assertTrue(state.ruleEMessage!!.contains("run-as com.dougie.app"))
        assertFalse(state.ruleEMessage!!.contains("现在几点"))
        assertFalse(state.ruleEMessage!!.contains("query_time"))
        assertNull(DebugUiState().ruleEMessage)
        assertFalse(DebugUiState().ruleEBusy)
    }

    @Test
    fun ruleBCopyNeverClaimsPassedOrLeaksUtterance() {
        assertEquals("评测 Kokoro 规则 B", RULE_B_ACTION_LABEL)
        assertEquals("本批自然度通过", RULE_B_NATURALNESS_LABEL)
        assertFalse(RULE_B_ACTION_LABEL.contains("已达标"))
        assertFalse(RULE_B_NATURALNESS_LABEL.contains("已达标"))
        val state = DebugUiState(
            ruleBBusy = false,
            ruleBMessage = "kokoro nLabeled=5 nScored=5 nUnscored=0 p95Rtf=0.5 " +
                "threadsApplied=true naturalnessApplied=false ruleBPassed=false\n" +
                "eval/tts/kokoro-rtf.jsonl\n" +
                "adb exec-out run-as com.dougie.app cat files/eval/tts/kokoro-rtf.jsonl",
        )
        assertFalse(state.toString().contains("已达标"))
        assertFalse(state.ruleBMessage!!.contains("已达标"))
        assertTrue(state.ruleBMessage!!.contains("run-as com.dougie.app"))
        assertFalse(state.ruleBMessage!!.contains("请把灯打开"))
        assertFalse(state.ruleBMessage!!.contains("PCM"))
        assertTrue(canMarkKokoroNaturalness(state.ruleBMessage))
        assertFalse(canMarkKokoroNaturalness(null))
        assertFalse(canMarkKokoroNaturalness(UserFacingErrors.KOKORO_EVAL_MODEL_MISSING))
        assertFalse(
            canMarkKokoroNaturalness(
                "kokoro nLabeled=4 nScored=4 nUnscored=0 p95Rtf=0.5 " +
                    "threadsApplied=true naturalnessApplied=false ruleBPassed=false",
            ),
        )
        assertNull(DebugUiState().ruleBMessage)
        assertFalse(DebugUiState().ruleBBusy)
    }
}
