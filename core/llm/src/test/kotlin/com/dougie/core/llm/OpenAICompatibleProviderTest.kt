package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.CloudLlmConfig
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        val provider = OpenAICompatibleProvider(OkHttpClient()) {
            CloudLlmConfig(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "sk-test",
                model = "gpt-4o-mini",
            )
        }

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
        assertTrue(recorded.body.readUtf8().contains("\"stream\":false"))
    }

    companion object {
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
    }
}
