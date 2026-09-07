package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** JVM test double: `return data` echoes JSON; `data.reduce` sums a JSON array. */
class FakeJsEvalPort(
    var ready: Boolean = true,
    var failWith: String? = null,
) : JsEvalPort {
    var lastScript: String = ""
    var lastData: String = ""

    override fun isReady(): Boolean = ready

    override fun evaluate(script: String, dataJson: String): String {
        lastScript = script
        lastData = dataJson
        failWith?.let { throw AgentException(it) }
        val body = script.trim().removeSuffix(";").trim()
        if (body == "return data" || body == "data") return dataJson
        if (body.contains("data.reduce")) {
            val el = Json.parseToJsonElement(dataJson) as? JsonArray
                ?: throw AgentException(UserFacingErrors.JS_EVAL_FAILED)
            var sum = 0.0
            for (item in el) {
                val n = item.jsonPrimitive.doubleOrNull
                    ?: throw AgentException(UserFacingErrors.JS_EVAL_FAILED)
                sum += n
            }
            return if (sum == sum.toLong().toDouble()) {
                sum.toLong().toString()
            } else {
                sum.toString()
            }
        }
        throw AgentException(UserFacingErrors.JS_EVAL_FAILED)
    }
}
