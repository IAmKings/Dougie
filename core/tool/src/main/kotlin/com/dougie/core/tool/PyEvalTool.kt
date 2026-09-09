package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamSpec
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class PyEvalTool(
    private val port: PyEvalPort,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override fun validateArguments(argumentsJson: String) {
        parse(argumentsJson)
    }

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val parsed = parse(argumentsJson)
        if (!port.isReady()) {
            return fail(UserFacingErrors.PY_ENGINE_NOT_READY)
        }
        val valueJson = try {
            withTimeout(TIMEOUT_MS) {
                port.evaluate(parsed.script, parsed.dataJson)
            }
        } catch (_: TimeoutCancellationException) {
            return fail(UserFacingErrors.PY_EVAL_TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            return fail(e.userMessage)
        } catch (_: Exception) {
            return fail(UserFacingErrors.PY_EVAL_FAILED)
        }
        val value = try {
            Json.parseToJsonElement(valueJson)
        } catch (_: Exception) {
            return fail(UserFacingErrors.PY_EVAL_FAILED)
        }
        return ToolResult(
            json = buildJsonObject {
                put("ok", true)
                put("value", value)
            }.toString(),
        )
    }

    private fun parse(argumentsJson: String): Parsed {
        val obj = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val script = obj.string("script")
        val dataEl = obj["data"] ?: throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        val dataRaw = when {
            dataEl is JsonNull ->
                throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
            dataEl is JsonPrimitive && dataEl.isString -> dataEl.content
            else -> dataEl.toString()
        }
        val dataJson = IsolatedJsGuard.canonicalizeData(dataRaw)
        IsolatedPyGuard.assertSize(script, dataJson)
        IsolatedPyGuard.assertNoHost(script)
        return Parsed(script = script, dataJson = dataJson)
    }

    private fun JsonObject.string(key: String): String {
        val value = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (value.isEmpty()) throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        return value
    }

    private fun fail(message: String): ToolResult =
        ToolResult(
            json = buildJsonObject {
                put("ok", false)
                put("error", message)
            }.toString(),
            isFatal = true,
            error = message,
        )

    private data class Parsed(val script: String, val dataJson: String)

    companion object {
        const val NAME = "py_eval"
        const val TIMEOUT_MS = 15_000L
        val DESCRIPTOR = ToolDescriptor(
            name = NAME,
            description = "Run an isolated Python program on JSON data (numpy/pandas). Last expression is JSON-serialized. App sandbox files only; no network. Requires confirmation.",
            properties = mapOf(
                "script" to ToolParamSpec(ToolParamType.STRING),
                "data" to ToolParamSpec(ToolParamType.OBJECT),
            ),
            riskLevel = RiskLevel.L4,
        )
    }
}
