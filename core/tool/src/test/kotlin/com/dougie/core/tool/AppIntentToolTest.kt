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
        val tool = AppIntentTool(port, allowedPackages = { setOf("com.android.settings") })
        tool.execute("""{"uri":"geo:0,0?q=coffee"}""", ToolContext("t", "g"))
        tool.execute("""{"uri":"package:com.android.settings"}""", ToolContext("t", "p"))
        assertEquals(2, port.launchCount)
        assertEquals("geo:0,0?q=coffee", port.launches[0].uri)
        assertEquals("package:com.android.settings", port.launches[1].uri)
    }

    @Test
    fun packageUriDeniedWhenNotOnUserList() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(port)
        try {
            tool.execute("""{"uri":"package:com.android.settings"}""", ToolContext("t", "p"))
            throw AssertionError("expected deny")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.APP_INTENT_NOT_ALLOWED, e.userMessage)
        }
        assertEquals(0, port.launchCount)
    }

    @Test
    fun httpsWithUnknownPackageIsNotAllowed() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(port)
        try {
            tool.execute(
                """{"uri":"https://example.com","package":"com.android.chrome"}""",
                ToolContext("t", "c"),
            )
            throw AssertionError("expected deny")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.APP_INTENT_NOT_ALLOWED, e.userMessage)
        }
        assertEquals(0, port.launchCount)
    }

    @Test
    fun httpsWithPackagePassesPackageToPort() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(port, allowedPackages = { setOf("com.android.chrome") })
        tool.execute(
            """{"uri":"https://example.com","package":"com.android.chrome"}""",
            ToolContext("t", "c"),
        )
        assertEquals("com.android.chrome", port.launches.single().packageName)
    }

    @Test
    fun packageUriWithProbeConfirmsVerifiedTrue() = runTest {
        val port = FakeAppIntentPort()
        val probe = FakeForegroundProbe(current = "com.android.settings")
        val tool = AppIntentTool(
            port,
            allowedPackages = { setOf("com.android.settings") },
            foregroundProbe = { probe },
        )
        val result = tool.execute("""{"uri":"package:com.android.settings"}""", ToolContext("t", "v"))
        assertEquals(true, result.verified)
        assertTrue(result.json.contains("\"verified\":true"))
        assertEquals(1, port.launchCount)
    }

    @Test
    fun verifyFailureIsNonFatalAndNotCached() = runTest {
        val port = FakeAppIntentPort()
        val probe = FakeForegroundProbe(current = "com.other.app")
        val store = InMemoryIdempotencyStore()
        val tool = AppIntentTool(
            port,
            idempotencyStore = store,
            allowedPackages = { setOf("com.android.settings") },
            foregroundProbe = { probe },
            verifyTimeoutMs = 500,
            verifyPollMs = 100,
        )
        val result = tool.execute("""{"uri":"package:com.android.settings"}""", ToolContext("t", "v"))
        assertEquals(false, result.verified)
        assertEquals(false, result.isFatal)
        assertEquals(UserFacingErrors.VERIFY_FAILED, result.error)
        assertTrue(result.json.contains("\"verified\":false"))
        assertEquals(null, store.get("tv"))
        assertEquals(1, port.launchCount)
    }

    @Test
    fun withoutProbeResultJsonStaysByteIdentical() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(port, allowedPackages = { setOf("com.android.settings") })
        val result = tool.execute("""{"uri":"package:com.android.settings"}""", ToolContext("t", "n"))
        assertEquals(null, result.verified)
        assertEquals("""{"ok":true,"launched":"package:com.android.settings"}""", result.json)
    }

    @Test
    fun httpUriWithProbeSkipsForegroundVerification() = runTest {
        val port = FakeAppIntentPort()
        val probe = FakeForegroundProbe(current = null)
        val tool = AppIntentTool(port, foregroundProbe = { probe })
        val result = tool.execute("""{"uri":"https://example.com"}""", ToolContext("t", "h"))
        assertEquals(null, result.verified)
        assertEquals(0, probe.callCount)
    }

    @Test
    fun explicitPackageArgIsVerifiedAgainstForeground() = runTest {
        val port = FakeAppIntentPort()
        val probe = FakeForegroundProbe(current = "com.android.chrome")
        val tool = AppIntentTool(
            port,
            allowedPackages = { setOf("com.android.chrome") },
            foregroundProbe = { probe },
        )
        val result = tool.execute(
            """{"uri":"https://example.com","package":"com.android.chrome"}""",
            ToolContext("t", "e"),
        )
        assertEquals(true, result.verified)
    }

    @Test
    fun idempotentReplayKeepsVerifiedFlag() = runTest {
        val port = FakeAppIntentPort()
        val probe = FakeForegroundProbe(current = "com.android.settings")
        val tool = AppIntentTool(
            port,
            allowedPackages = { setOf("com.android.settings") },
            foregroundProbe = { probe },
        )
        val context = ToolContext("t", "r")
        val first = tool.execute("""{"uri":"package:com.android.settings"}""", context)
        val second = tool.execute("""{"uri":"package:com.android.settings"}""", context)
        assertEquals(true, second.verified)
        assertEquals(first.json, second.json)
        assertEquals(1, port.launchCount)
    }

    @Test
    fun probeProviderWithoutObserverKeepsResultUnverified() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(
            port,
            allowedPackages = { setOf("com.android.settings") },
            foregroundProbe = { null },
        )
        val result = tool.execute("""{"uri":"package:com.android.settings"}""", ToolContext("t", "b"))
        assertEquals(null, result.verified)
        assertEquals("""{"ok":true,"launched":"package:com.android.settings"}""", result.json)
        assertEquals(1, port.launchCount)
    }

    @Test
    fun probePresentButBlindReportsVerifyFailed() = runTest {
        val port = FakeAppIntentPort()
        val tool = AppIntentTool(
            port,
            allowedPackages = { setOf("com.android.settings") },
            foregroundProbe = { FakeForegroundProbe(current = null) },
            verifyTimeoutMs = 300,
            verifyPollMs = 100,
        )
        val result = tool.execute("""{"uri":"package:com.android.settings"}""", ToolContext("t", "x"))
        assertEquals(false, result.verified)
        assertEquals(UserFacingErrors.VERIFY_FAILED, result.error)
        assertEquals(false, result.isFatal)
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
