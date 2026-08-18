package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.CloudLlmConfig
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.MemoryEntry
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamSpec
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OpenAICompatibleProviderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesToolCallThenFinalContentAcrossTwoGenerateCalls() = runTest {
        server.enqueue(MockResponse().setBody(TOOL_CALL_BODY))
        server.enqueue(MockResponse().setBody(FINAL_BODY))
        val provider = testProvider()

        val first = provider.generate(LoopContext(AgentTask(taskId = "t1", input = "电量?")))
        val toolCall = first as LlmResponse.ToolCall
        assertEquals("call_battery_1", toolCall.id)
        assertEquals("battery", toolCall.name)
        assertEquals("{}", toolCall.argsJson)

        val second = provider.generate(
            LoopContext(
                AgentTask(
                    taskId = "t1",
                    input = "电量?",
                    toolTrace = listOf(
                        ToolTraceEntry(
                            toolCallId = toolCall.id,
                            toolName = toolCall.name,
                            argsSummary = toolCall.argsJson,
                            resultJson = """{"battery_percent":80,"charging":false}""",
                            status = ToolTraceStatus.SUCCESS,
                        ),
                    ),
                ),
            ),
        )
        val answer = second as LlmResponse.FinalAnswer
        assertTrue(answer.text.contains("80"))

        assertEquals(2, server.requestCount)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.endsWith("/chat/completions"))
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("\"max_tokens\":2048"))
        assertTrue(body.contains("\"name\":\"time\""))
        assertTrue(body.contains("\"name\":\"battery\""))
        assertTrue(body.contains("\"name\":\"calendar_query\""))
        assertTrue(body.contains("\"name\":\"calendar_create\""))
        assertTrue(body.contains("\"name\":\"clipboard_read\""))
        assertTrue(body.contains("\"name\":\"clipboard_write\""))
        assertTrue(body.contains("\"name\":\"location\""))
        assertTrue(body.contains("\"name\":\"screen_capture\""))
        assertTrue(body.contains("\"name\":\"screen_match\""))
        assertTrue(body.contains("\"name\":\"app_intent\""))
    }

    @Test
    fun streamsTextDeltasIntoFinalAnswer() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(TEXT_SSE),
        )
        val provider = testProvider()
        val events = provider.stream(LoopContext(AgentTask(taskId = "t1", input = "你好"))).toList()
        val texts = events.map { (it as LlmEvent.TextDelta).text }
        assertEquals(listOf("你", "现在", "的手机电量是 80%。"), texts)
        assertEquals("你现在的手机电量是 80%。", texts.joinToString(""))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun screenCaptureToolResultInMessagesIsMetadataOnly() = runTest {
        server.enqueue(MockResponse().setBody(FINAL_BODY))
        val provider = testProvider()
        provider.generate(
            LoopContext(
                AgentTask(
                    taskId = "cap",
                    input = "截一下屏",
                    toolTrace = listOf(
                        ToolTraceEntry(
                            toolCallId = "call_cap_1",
                            toolName = "screen_capture",
                            argsSummary = "{}",
                            resultJson = """{"capture_id":"synthetic","width":32,"height":32}""",
                            status = ToolTraceStatus.SUCCESS,
                        ),
                    ),
                ),
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("capture_id"))
        assertTrue(body.contains("synthetic"))
        assertTrue(body.contains("width"))
        assertTrue(body.contains("height"))
        assertTrue(!body.contains("data:image"))
        assertTrue(!body.contains("base64,"))
        assertTrue(!body.contains("\"gray\":"))
    }

    @Test
    fun requestBodyIncludesKnownFactsFromRetrievedMemories() = runTest {
        server.enqueue(MockResponse().setBody(FINAL_BODY))
        val provider = testProvider()
        provider.generate(
            LoopContext(
                AgentTask(
                    taskId = "t-mem",
                    input = "我叫什么",
                    retrievedMemories = listOf(
                        MemoryEntry(
                            id = "m1",
                            content = "我叫小明，住在上海",
                            source = "task-0",
                            confidence = 0.8f,
                            createdAt = 1L,
                            updatedAt = 1L,
                        ),
                    ),
                ),
            ),
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("Known facts"))
        assertTrue(body.contains("我叫小明，住在上海"))
        assertTrue(body.contains("我叫什么"))
    }

    @Test
    fun assemblesStreamedToolCallArguments() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(TOOL_CALL_SSE),
        )
        val provider = testProvider()
        val events = provider.stream(LoopContext(AgentTask(taskId = "t1", input = "电量?"))).toList()
        val tool = events.single() as LlmEvent.ToolCall
        assertEquals("call_battery_1", tool.id)
        assertEquals("battery", tool.name)
        assertEquals("{}", tool.argsJson)
    }

    @Test
    fun retriesNetworkFailureTwiceThenSucceeds() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody(FINAL_BODY))
        val provider = testProvider()
        val answer = provider.generate(LoopContext(AgentTask(taskId = "t1", input = "你好")))
        assertTrue((answer as LlmResponse.FinalAnswer).text.contains("80"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun stopsAfterThreeNetworkFailures() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        }
        val provider = testProvider()
        try {
            provider.generate(LoopContext(AgentTask(taskId = "t1", input = "你好")))
            throw AssertionError("expected network failure")
        } catch (e: Exception) {
            val network = e is IOException ||
                (e is com.dougie.core.model.AgentException && e.userMessage == UserFacingErrors.NETWORK_FAILED)
            assertTrue(network)
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun doesNotRetryAfterFirstStreamEvent() = runTest {
        val firstEvent =
            """data: {"choices":[{"delta":{"content":"你好"}}]}""" + "\n\n"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(firstEvent + " ".repeat(2048) + "data: {\"choices\":[{\"delta\":{\"content\":\"续\"}}]}\n\n")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val provider = testProvider()
        try {
            provider.generate(LoopContext(AgentTask(taskId = "t1", input = "你好")))
            throw AssertionError("expected stream failure after first event")
        } catch (e: Exception) {
            val failed = e is IOException ||
                (e is com.dougie.core.model.AgentException &&
                    (e.userMessage == UserFacingErrors.NETWORK_FAILED ||
                        e.userMessage == UserFacingErrors.LLM_FAILED))
            assertTrue(failed)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun requestJsonIncludesClampedMaxTokens() {
        val json = testProvider().buildRequestJson(
            model = "gpt-4o-mini",
            task = AgentTask(taskId = "t1", input = "hi"),
            stream = true,
            maxTokens = 9,
        )
        assertTrue(json.contains("\"max_tokens\":16"))
    }

    private fun testProvider(): OpenAICompatibleProvider {
        return OpenAICompatibleProvider(
            client = OkHttpClient(),
            config = {
                CloudLlmConfig(
                    baseUrl = server.url("/v1/").toString(),
                    apiKey = "sk-test",
                    model = "gpt-4o-mini",
                )
            },
            toolDescriptors = { PHASE_3B_TOOLS },
        )
    }

    companion object {
        private val PHASE_3B_TOOLS = listOf(
            ToolDescriptor("battery", description = "Read the device battery percent and charging state."),
            ToolDescriptor("time", description = "Read the current local date and time."),
            ToolDescriptor(
                name = "calendar_query",
                description = "Query upcoming calendar events as a short JSON summary.",
            ),
            ToolDescriptor(
                name = "calendar_create",
                description = "Create a calendar event. Requires title and startIso.",
                properties = mapOf(
                    "title" to ToolParamSpec(ToolParamType.STRING),
                    "startIso" to ToolParamSpec(ToolParamType.STRING),
                ),
            ),
            ToolDescriptor(
                name = "clipboard_read",
                description = "Read clipboard text. Only works while the app is in the foreground.",
            ),
            ToolDescriptor(
                name = "clipboard_write",
                description = "Write text to the clipboard. Requires user confirmation.",
                properties = mapOf("text" to ToolParamSpec(ToolParamType.STRING)),
            ),
            ToolDescriptor(
                name = "location",
                description = "Read a coarse device location as JSON (latitude, longitude, accuracy, provider).",
            ),
            ToolDescriptor(
                name = "screen_capture",
                description = "Capture the current screen. Returns capture_id, width, and height only.",
            ),
            ToolDescriptor(
                name = "screen_match",
                description = "Match a bundled grayscale template against the last screen capture.",
                properties = mapOf("template_id" to ToolParamSpec(ToolParamType.STRING)),
            ),
            ToolDescriptor(
                name = "app_intent",
                description = "Open an allowed http(s)/geo link or launch an installed app. Requires user confirmation. Forbidden: tel, sms, file, javascript, content, intent.",
                properties = mapOf(
                    "uri" to ToolParamSpec(ToolParamType.STRING),
                    "package" to ToolParamSpec(ToolParamType.STRING, defaultJson = "\"\""),
                ),
            ),
        )
        private const val TOOL_CALL_BODY = """
        {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": null,
                "tool_calls": [
                  {
                    "id": "call_battery_1",
                    "type": "function",
                    "function": { "name": "battery", "arguments": "{}" }
                  }
                ]
              }
            }
          ]
        }
        """

        private const val FINAL_BODY = """
        {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "你现在的手机电量是 80%。"
              }
            }
          ]
        }
        """

        private val TEXT_SSE = """
            data: {"choices":[{"delta":{"content":"你"}}]}

            data: {"choices":[{"delta":{"content":"现在"}}]}

            data: {"choices":[{"delta":{"content":"的手机电量是 80%。"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent() + "\n"

        private val TOOL_CALL_SSE = """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_battery_1","type":"function","function":{"name":"battery","arguments":""}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}

            data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

            data: [DONE]

        """.trimIndent() + "\n"
    }
}
