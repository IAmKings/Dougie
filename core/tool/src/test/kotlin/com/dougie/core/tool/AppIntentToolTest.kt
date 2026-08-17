package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIntentToolTest {

    @Test
    fun httpsLaunchesOnceAfterExecute() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(port)
        val result = tool.execute(
            """{"uri":"https://example.com"}""",
            ToolContext("task-1", "call-1"),
        )
        assertEquals(1, port.launchCount)
        assertTrue(result.json.contains("https://example.com"))
        assertEquals(false, result.isFatal)
    }

    @Test
    fun rejectedSchemesNeverCallPort() = runTest {
        val schemes = listOf(
            "tel:123456",
            "sms:123456",
            "file:///sdcard/secret.txt",
            "javascript:alert(1)",
            "content://com.android.contacts/data/1",
            "mailto:a@b.com",
            "intent://scan/#Intent;scheme=zxing;end",
        )
        for (uri in schemes) {
            val port = FakeAppIntentPort()
            val tool = AppIntentTool(port)
            try {
                tool.execute("""{"uri":"$uri"}""", ToolContext("t", "c"))
                throw AssertionError("expected deny for $uri")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.APP_INTENT_DENIED, e.userMessage)
            }
            assertEquals(uri, 0, port.launchCount)
        }
    }

    @Test
    fun sameIdempotencyKeyLaunchesOnce() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(port)
        val context = ToolContext(taskId = "task-1", toolCallId = "call-1")
        val args = """{"uri":"https://example.com/path"}"""
        val first = tool.execute(args, context)
        val second = tool.execute(args, context)
        assertEquals(first.json, second.json)
        assertEquals(1, port.launchCount)
    }

    @Test
    fun backgroundIsFatalAndDoesNotLaunch() = runTest {
        val port = FakeAppIntentPort(foreground = false)
        val tool = AppIntentTool(port)
        val result = tool.execute(
            """{"uri":"https://example.com"}""",
            ToolContext("t", "c"),
        )
        assertEquals(true, result.isFatal)
        assertEquals(UserFacingErrors.APP_INTENT_NOT_FOREGROUND, result.error)
        assertEquals(0, port.launchCount)
    }

    @Test
    fun geoAndPackageUrisLaunch() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(port)
        tool.execute("""{"uri":"geo:0,0?q=coffee"}""", ToolContext("t", "g"))
        tool.execute("""{"uri":"package:com.android.settings"}""", ToolContext("t", "p"))
        assertEquals(2, port.launchCount)
        assertEquals("geo:0,0?q=coffee", port.launches[0].uri)
        assertEquals("package:com.android.settings", port.launches[1].uri)
    }

    @Test
    fun httpsWithPackagePassesPackageToPort() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(port)
        tool.execute(
            """{"uri":"https://example.com","package":"com.android.chrome"}""",
            ToolContext("t", "c"),
        )
        assertEquals("com.android.chrome", port.launches.single().packageName)
    }
}

class AppIntentAllowlistTest {

    @Test
    fun httpRequiresHost() {
        try {
            AppIntentAllowlist.validate("https://", null)
            throw AssertionError("expected deny")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.APP_INTENT_DENIED, e.userMessage)
        }
    }
}
