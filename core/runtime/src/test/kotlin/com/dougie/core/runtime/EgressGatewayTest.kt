package com.dougie.core.runtime

import com.dougie.core.llm.LlmProvider
import com.dougie.core.llm.OpenAICompatibleProvider
import com.dougie.core.model.AgentTask
import com.dougie.core.model.CloudLlmConfig
import com.dougie.core.model.EgressBlockedException
import com.dougie.core.model.EgressPolicy
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.MissingApiKeyException
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EgressGatewayTest {

    @Test
    fun denyDoesNotInvokeCloudProvider() = runTest {
        val provider = CountingProvider()
        val gateway = EgressGateway(
            policy = { EgressPolicy(allowCloud = false) },
            apiKey = { "sk-present-but-blocked" },
        )
        try {
            gateway.complete(provider, LoopContext(AgentTask("t", "hi")))
            throw AssertionError("expected EgressBlockedException")
        } catch (e: EgressBlockedException) {
            assertEquals(UserFacingErrors.EGRESS_BLOCKED, e.userMessage)
        }
        assertFalse(provider.called)
        assertEquals(0, provider.calls)
    }

    @Test
    fun allowCloudWithoutKeyDoesNotInvokeProvider() = runTest {
        val provider = CountingProvider()
        val gateway = EgressGateway(
            policy = { EgressPolicy(allowCloud = true) },
            apiKey = { "  " },
        )
        try {
            gateway.complete(provider, LoopContext(AgentTask("t", "hi")))
            throw AssertionError("expected MissingApiKeyException")
        } catch (e: MissingApiKeyException) {
            assertEquals(UserFacingErrors.MISSING_API_KEY, e.userMessage)
        }
        assertFalse(provider.called)
    }

    @Test
    fun denyNeverSendsHttpEvenWhenApiKeyIsConfigured() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"no"}}]}"""))
            val provider = OpenAICompatibleProvider(OkHttpClient()) {
                CloudLlmConfig(
                    baseUrl = server.url("/v1/").toString(),
                    apiKey = "sk-present-but-blocked",
                    model = "gpt-4o-mini",
                )
            }
            val gateway = EgressGateway(
                policy = { EgressPolicy(allowCloud = false) },
                apiKey = { "sk-present-but-blocked" },
            )
            try {
                gateway.complete(provider, LoopContext(AgentTask("t", "电量?")))
                throw AssertionError("expected EgressBlockedException")
            } catch (e: EgressBlockedException) {
                assertEquals(UserFacingErrors.EGRESS_BLOCKED, e.userMessage)
            }
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private class CountingProvider : LlmProvider {
        override val isLocal: Boolean = false
        var calls: Int = 0
        val called: Boolean get() = calls > 0

        override suspend fun generate(context: LoopContext): LlmResponse {
            calls += 1
            return LlmResponse.FinalAnswer("should-not-run")
        }
    }
}
