package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberTest {
    @Test
    fun stripsSpacesAndDashesAndKeepsOptionalPlus() {
        assertEquals("13800138000", PhoneNumber.canonical("138-0013 8000"))
        assertEquals("+8613800138000", PhoneNumber.canonical("+86 138-0013-8000"))
        assertEquals("12345678", PhoneNumber.canonical("12345678"))
        assertEquals("1".repeat(20), PhoneNumber.canonical("1".repeat(20)))
    }

    @Test
    fun rejectsShortLettersOrTooLong() {
        assertInvalid("1234567")
        assertInvalid("1234567a")
        assertInvalid("+1234567")
        assertInvalid("1".repeat(21))
        assertInvalid("++8613800138000")
        assertInvalid("")
    }

    private fun assertInvalid(raw: String) {
        try {
            PhoneNumber.canonical(raw)
            throw AssertionError("expected invalid for $raw")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
    }
}

class SmsComposeToolTest {
    @Test
    fun validNumberLaunchesOnce() = runTest {
        val port = FakeTelecomPort()
        val tool = SmsComposeTool(port)
        val result = tool.execute(
            """{"to":"138-0013-8000","body":"今晚见"}""",
            ToolContext("task-1", "call-1"),
        )
        assertEquals(false, result.isFatal)
        assertEquals(1, port.composeCount)
        assertEquals("13800138000", port.lastCompose?.to)
        assertEquals("今晚见", port.lastCompose?.body)
        assertTrue(result.json.contains("\"ok\":true"))
        assertEquals(RiskLevel.L3, tool.descriptor.riskLevel)
        assertEquals(null, tool.descriptor.androidPermission)
    }

    @Test
    fun emptyOrTooLongBodyIsInvalidAndDoesNotLaunch() = runTest {
        val port = FakeTelecomPort()
        val tool = SmsComposeTool(port)
        assertInvalidArgs(tool, """{"to":"123","body":"hi"}""", port)
        assertInvalidArgs(tool, """{"to":"13800138000","body":""}""", port)
        assertInvalidArgs(tool, """{"to":"13800138000","body":"   "}""", port)
        val tooLong = "a".repeat(SmsComposeTool.MAX_BODY_UTF16 + 1)
        assertInvalidArgs(tool, """{"to":"13800138000","body":"$tooLong"}""", port)
        val maxOk = "a".repeat(SmsComposeTool.MAX_BODY_UTF16)
        val result = tool.execute(
            """{"to":"13800138000","body":"$maxOk"}""",
            ToolContext("t-max", "c-max"),
        )
        assertEquals(false, result.isFatal)
        assertEquals(1, port.composeCount)
        assertEquals(maxOk.length, port.lastCompose?.body?.length)
    }

    @Test
    fun sameIdempotencyKeyComposesOnce() = runTest {
        val port = FakeTelecomPort()
        val tool = SmsComposeTool(port)
        val context = ToolContext(taskId = "task-1", toolCallId = "call-1")
        val args = """{"to":"13800138000","body":"hi"}"""
        val first = tool.execute(args, context)
        val second = tool.execute(args, context)
        assertEquals(first.json, second.json)
        assertEquals(1, port.composeCount)
    }

    @Test
    fun backgroundIsFatalAndDoesNotCompose() = runTest {
        val port = FakeTelecomPort(foreground = false)
        val tool = SmsComposeTool(port)
        val result = tool.execute(
            """{"to":"13800138000","body":"hi"}""",
            ToolContext("t", "c"),
        )
        assertEquals(true, result.isFatal)
        assertEquals(UserFacingErrors.APP_INTENT_NOT_FOREGROUND, result.error)
        assertEquals(0, port.composeCount)
        assertNull(port.lastCompose)
    }

    @Test
    fun launchFailureIsFatalWithTelecomCopy() = runTest {
        val port = FakeTelecomPort(failLaunch = true)
        val tool = SmsComposeTool(port)
        val result = tool.execute(
            """{"to":"13800138000","body":"hi"}""",
            ToolContext("t", "c"),
        )
        assertEquals(true, result.isFatal)
        assertEquals(UserFacingErrors.TELECOM_LAUNCH_FAILED, result.error)
        assertEquals(0, port.composeCount)
    }

    private fun assertInvalidArgs(tool: SmsComposeTool, args: String, port: FakeTelecomPort) {
        try {
            tool.validateArguments(args)
            throw AssertionError("expected invalid args")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
        assertEquals(0, port.composeCount)
    }
}

class PhoneDialToolTest {
    @Test
    fun validNumberDialsOnce() = runTest {
        val port = FakeTelecomPort()
        val tool = PhoneDialTool(port)
        val result = tool.execute(
            """{"number":"+86 138-0013-8000"}""",
            ToolContext("task-1", "call-1"),
        )
        assertEquals(false, result.isFatal)
        assertEquals(1, port.dialCount)
        assertEquals("+8613800138000", port.lastDial)
        assertEquals(RiskLevel.L3, tool.descriptor.riskLevel)
        assertEquals(null, tool.descriptor.androidPermission)
    }

    @Test
    fun invalidNumberDoesNotDial() = runTest {
        val port = FakeTelecomPort()
        val tool = PhoneDialTool(port)
        try {
            tool.execute("""{"number":"123"}""", ToolContext("t", "c"))
            throw AssertionError("expected invalid")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
        assertEquals(0, port.dialCount)
        assertNull(port.lastDial)
    }

    @Test
    fun sameIdempotencyKeyDialsOnce() = runTest {
        val port = FakeTelecomPort()
        val tool = PhoneDialTool(port)
        val context = ToolContext(taskId = "task-1", toolCallId = "call-1")
        val args = """{"number":"13800138000"}"""
        tool.execute(args, context)
        tool.execute(args, context)
        assertEquals(1, port.dialCount)
    }

    @Test
    fun backgroundIsFatalAndDoesNotDial() = runTest {
        val port = FakeTelecomPort(foreground = false)
        val tool = PhoneDialTool(port)
        val result = tool.execute(
            """{"number":"13800138000"}""",
            ToolContext("t", "c"),
        )
        assertEquals(true, result.isFatal)
        assertEquals(UserFacingErrors.APP_INTENT_NOT_FOREGROUND, result.error)
        assertEquals(0, port.dialCount)
    }

    @Test
    fun launchFailureIsFatalWithTelecomCopy() = runTest {
        val port = FakeTelecomPort(failLaunch = true)
        val tool = PhoneDialTool(port)
        val result = tool.execute(
            """{"number":"13800138000"}""",
            ToolContext("t", "c"),
        )
        assertEquals(true, result.isFatal)
        assertEquals(UserFacingErrors.TELECOM_LAUNCH_FAILED, result.error)
        assertEquals(0, port.dialCount)
    }
}
