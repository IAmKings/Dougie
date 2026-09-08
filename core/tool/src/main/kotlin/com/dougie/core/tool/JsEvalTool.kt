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

class JsEvalTool(
    private val port: JsEvalPort,
    private val privileged: () -> Boolean = { false },
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor
        get() = if (privileged()) DESCRIPTOR_L4 else DESCRIPTOR_L2

    override fun validateArguments(argumentsJson: String) {
        parse(argumentsJson)
    }

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val parsed = parse(argumentsJson)
        if (!port.isReady()) {
            return fail(UserFacingErrors.JS_ENGINE_NOT_READY)
        }
        val valueJson = try {
            withTimeout(TIMEOUT_MS) {
                port.evaluate(parsed.script, parsed.dataJson, asProgram = privileged())
            }
        } catch (_: TimeoutCancellationException) {
            return fail(UserFacingErrors.JS_EVAL_TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            return fail(e.userMessage)
        } catch (_: Exception) {
            return fail(UserFacingErrors.JS_EVAL_FAILED)
        }
        val value = try {
            Json.parseToJsonElement(valueJson)
        } catch (_: Exception) {
            return fail(UserFacingErrors.JS_EVAL_FAILED)
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
        IsolatedJsGuard.assertSize(script, dataJson)
        IsolatedJsGuard.assertNoHost(script)
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
        const val NAME = "js_eval"
        const val TIMEOUT_MS = 2_000L
        val DESCRIPTOR: ToolDescriptor get() = DESCRIPTOR_L2
        val DESCRIPTOR_L2 = ToolDescriptor(
            name = NAME,
            description = "Run an isolated JavaScript function body on JSON data (array, object, or JSON text). No network or files. Requires confirmation.",
            properties = mapOf(
                "script" to ToolParamSpec(ToolParamType.STRING),
                "data" to ToolParamSpec(ToolParamType.OBJECT),
            ),
            riskLevel = RiskLevel.L2,
        )
        val DESCRIPTOR_L4 = DESCRIPTOR_L2.copy(
            description = "Run an isolated JavaScript program. Last expression is JSON-serialized. No network or files. Requires confirmation.",
            riskLevel = RiskLevel.L4,
        )
    }
}
