package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPlaybackTest {
    @Test
    fun typedSendDoesNotSpeak() {
        val task = AgentTask(
            taskId = "t1",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
            speakReply = false,
        )
        assertFalse(ReplyPlayback.shouldSpeak(task, TaskStatus.THINKING, null))
    }

    @Test
    fun voiceSendSpeaksOnceOnCompletedTransition() {
        val task = AgentTask(
            taskId = "t1",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
            speakReply = true,
        )
        assertTrue(ReplyPlayback.shouldSpeak(task, TaskStatus.THINKING, null))
        assertFalse(ReplyPlayback.shouldSpeak(task, TaskStatus.THINKING, "t1"))
        assertFalse(ReplyPlayback.shouldSpeak(task, TaskStatus.COMPLETED, null))
        assertFalse(ReplyPlayback.shouldSpeak(task, null, null))
    }

    @Test
    fun restoredSnapshotDoesNotRespeak() {
        val task = AgentTask(
            taskId = "old",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
            speakReply = true,
        )
        assertFalse(ReplyPlayback.shouldSpeak(task, previousStatus = null, spokenTaskId = null))
    }

    @Test
    fun retryFromFailedSpeaksOnNewCompleted() {
        val task = AgentTask(
            taskId = "t2",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
            speakReply = true,
        )
        assertTrue(ReplyPlayback.shouldSpeak(task, TaskStatus.FAILED, "t1"))
    }

    @Test
    fun stopDoesNotChangeCompleted() {
        val task = AgentTask(
            taskId = "t1",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
            speakReply = true,
        )
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertFalse(ReplyPlayback.shouldSpeak(task, TaskStatus.COMPLETED, "t1"))
    }
}
