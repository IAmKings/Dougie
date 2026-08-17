package com.dougie.core.llm

import com.dougie.core.model.AgentException
import com.dougie.core.model.AgentTask
import com.dougie.core.model.CloudLlmConfig
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OpenAICompatibleProvider(
    private val client: OkHttpClient,
    private val config: () -> CloudLlmConfig,
) : LlmProvider {
    override val isLocal: Boolean = false

    override suspend fun generate(context: LoopContext): LlmResponse {
        val cfg = config()
        val url = cfg.baseUrl.trimEnd('/') + "/chat/completions"
        val payload = buildRequestJson(cfg.model, context.task)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw AgentException(UserFacingErrors.NETWORK_FAILED)
        }
        response.use { http ->
            val body = http.body?.string().orEmpty()
            if (!http.isSuccessful) {
                throw AgentException(UserFacingErrors.LLM_FAILED)
            }
            return parseResponse(body)
        }
    }

    internal fun parseResponse(body: String): LlmResponse {
        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw AgentException(UserFacingErrors.LLM_FAILED)
        }
        val message = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: throw AgentException(UserFacingErrors.LLM_FAILED)
        val toolCalls = message["tool_calls"] as? JsonArray
        val firstCall = toolCalls?.firstOrNull()?.jsonObject
        if (firstCall != null) {
            val fn = firstCall["function"]?.jsonObject ?: throw AgentException(UserFacingErrors.LLM_FAILED)
            return LlmResponse.ToolCall(
                id = firstCall["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = fn["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                argsJson = argumentJson(fn["arguments"]),
            )
        }
        val content = textContent(message["content"])
        return LlmResponse.FinalAnswer(content)
    }

    internal fun buildRequestJson(model: String, task: AgentTask): String {
        val messages = buildJsonArray {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                },
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", task.input)
                },
            )
            for (trace in task.toolTrace) {
                add(
                    buildJsonObject {
                        put("role", "assistant")
                        put(
                            "tool_calls",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("id", trace.toolCallId)
                                        put("type", "function")
                                        put(
                                            "function",
                                            buildJsonObject {
                                                put("name", trace.toolName)
                                                put("arguments", trace.argsSummary.ifBlank { "{}" })
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
                val result = trace.resultJson ?: continue
                add(
                    buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", trace.toolCallId)
                        put("content", result)
                    },
                )
            }
        }
        return buildJsonObject {
            put("model", model)
            put("stream", false)
            put("messages", messages)
            put("tools", BATTERY_TOOLS)
        }.toString()
    }

    private fun argumentJson(element: JsonElement?): String {
        if (element == null || element is JsonNull) return "{}"
        if (element is JsonPrimitive) return element.content.ifBlank { "{}" }
        return element.toString()
    }

    private fun textContent(element: JsonElement?): String {
        if (element == null || element is JsonNull) return ""
        if (element is JsonPrimitive) return element.content
        return element.toString()
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val SYSTEM_PROMPT =
            "You are Dougie, a local-first mobile agent. Use the battery tool when the user asks about battery level. Reply in Chinese."
        private val BATTERY_TOOLS = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "function")
                    put(
                        "function",
                        buildJsonObject {
                            put("name", "battery")
                            put("description", "Read the device battery percent and charging state.")
                            put(
                                "parameters",
                                buildJsonObject {
                                    put("type", "object")
                                    put("properties", buildJsonObject { })
                                },
                            )
                        },
                    )
                },
            )
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response)
            }
        },
    )
    cont.invokeOnCancellation { cancel() }
}
