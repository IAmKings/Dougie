package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.model.CompletionPath
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenShortcutHintTest {
    @Test
    fun showsOnlyForLocalScreenCaptureSuccess() {
        val ok = AgentTask(
            taskId = "t",
            input = "截个屏",
            status = TaskStatus.COMPLETED,
            completionPath = CompletionPath.LOCAL_INTENT,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "intent-route-1",
                    toolName = "screen_capture",
                    argsSummary = "{}",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
        )
        assertTrue(ScreenShortcutHint.shouldShow(ok))
        assertFalse(ScreenShortcutHint.shouldShow(ok.copy(completionPath = CompletionPath.REMOTE_LLM)))
        assertFalse(ScreenShortcutHint.shouldShow(ok.copy(status = TaskStatus.FAILED)))
        assertFalse(
            ScreenShortcutHint.shouldShow(
                ok.copy(
                    toolTrace = listOf(
                        ToolTraceEntry("1", "battery", "{}", status = ToolTraceStatus.SUCCESS),
                    ),
                ),
            ),
        )
        assertFalse(ScreenShortcutHint.shouldShow(null))
        assertFalse(
            ScreenShortcutHint.shouldShow(
                ok.copy(
                    toolTrace = listOf(
                        ToolTraceEntry(
                            toolCallId = "intent-route-1",
                            toolName = "screen_capture",
                            argsSummary = "{}",
                            status = ToolTraceStatus.FAILED,
                        ),
                    ),
                ),
            ),
        )
    }
}
