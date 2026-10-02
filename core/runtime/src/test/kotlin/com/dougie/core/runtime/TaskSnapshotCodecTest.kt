package com.dougie.core.runtime

import com.dougie.core.model.AgentTask
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskSnapshotCodecTest {

    @Test
    fun verifiedFlagSurvivesSnapshotRoundTrip() {
        val task = AgentTask(
            taskId = "t-codec",
            input = "打开微信",
            status = TaskStatus.COMPLETED,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "c1",
                    toolName = "app_intent",
                    argsSummary = """{"uri":"package:com.example.wechat"}""",
                    resultJson = """{"ok":false,"error":"e","verified":false}""",
                    status = ToolTraceStatus.SUCCESS,
                    riskLevel = RiskLevel.L2,
                    verified = false,
                ),
                ToolTraceEntry(
                    toolCallId = "c2",
                    toolName = "battery",
                    argsSummary = "{}",
                    resultJson = """{"ok":true}""",
                    status = ToolTraceStatus.SUCCESS,
                    riskLevel = RiskLevel.L0,
                    verified = true,
                ),
                ToolTraceEntry(
                    toolCallId = "c3",
                    toolName = "time",
                    argsSummary = "{}",
                    resultJson = """{"ok":true}""",
                    status = ToolTraceStatus.SUCCESS,
                    riskLevel = RiskLevel.L0,
                    verified = null,
                ),
            ),
        )
        val decoded = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(task))
        assertEquals(false, decoded.toolTrace[0].verified)
        assertEquals(true, decoded.toolTrace[1].verified)
        assertNull(decoded.toolTrace[2].verified)
    }

    @Test
    fun legacySnapshotWithoutVerifiedKeyDecodesAsUnverified() {
        val legacy = """
            {"taskId":"t-legacy","input":"打开微信","status":"COMPLETED","loopCount":1,"maxLoops":8,
             "toolTrace":[{"toolCallId":"c1","toolName":"app_intent","argsSummary":"{}",
             "resultJson":"{\"ok\":true}","status":"SUCCESS","riskLevel":"L2"}],
             "retrievedMemories":[],"retrievedConversationHits":[],"attachments":[],
             "speakReply":false,"conversationId":"default"}
        """.trimIndent()
        val decoded = TaskSnapshotCodec.decode(legacy)
        assertNull(decoded.toolTrace.single().verified)
    }
}
