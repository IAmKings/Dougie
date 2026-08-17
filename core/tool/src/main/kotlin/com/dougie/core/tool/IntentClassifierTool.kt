package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolContext
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamSpec
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.ToolResult
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class IntentClassifierTool(
    private val port: IntentPort,
) : AgentTool {
    override val name: String = NAME
    override val descriptor: ToolDescriptor = DESCRIPTOR

    override fun validateArguments(argumentsJson: String) {
        parseText(argumentsJson)
    }

    override suspend fun execute(argumentsJson: String, context: ToolContext): ToolResult {
        require(context.idempotencyKey == context.taskId + context.toolCallId)
        val text = parseText(argumentsJson)
        if (!port.isModelPresent()) {
            return fail(UserFacingErrors.INTENT_MODEL_MISSING)
        }
        if (!port.isEngineReady()) {
            return fail(UserFacingErrors.INTENT_ENGINE_NOT_READY)
        }
        val hit = port.classify(text)
        if (hit.confidence < IntentModelLayout.MIN_CONFIDENCE) {
            return fail(UserFacingErrors.INTENT_LOW_CONFIDENCE)
        }
        return ToolResult(
            json = buildJsonObject {
                put("ok", true)
                put("intent", hit.intent)
                put("slots", buildJsonObject {
                    hit.slots.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
                })
                put("route", hit.route)
                put("confidence", hit.confidence)
            }.toString(),
        )
    }

    private fun parseText(argumentsJson: String): String {
        val obj = try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (text.isEmpty()) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        return text
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

    companion object {
        const val NAME = "intent_classifier"
        val DESCRIPTOR = ToolDescriptor(
            name = NAME,
            description = "Classify on-device Chinese intent and slots from text. Does not replace the cloud LLM and never sends text off-device.",
            properties = mapOf(
                "text" to ToolParamSpec(ToolParamType.STRING),
            ),
            riskLevel = RiskLevel.L0,
        )
    }
}
