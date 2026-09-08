package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** JVM test double: L2 needs `return`; L4 program uses last expression. */
class FakeJsEvalPort(
    var ready: Boolean = true,
    var failWith: String? = null,
) : JsEvalPort {
    var lastScript: String = ""
    var lastData: String = ""
    var lastAsProgram: Boolean = false

    override fun isReady(): Boolean = ready

    override fun evaluate(script: String, dataJson: String, asProgram: Boolean): String {
        lastScript = script
        lastData = dataJson
        lastAsProgram = asProgram
        failWith?.let { throw AgentException(it) }
        val body = script.trim().removeSuffix(";").trim()
        if (!asProgram && (body == "return data")) return dataJson
        if (asProgram && (body == "data" || body == "return data")) return dataJson
        if (body.contains("data.reduce")) {
            if (!asProgram && !body.contains("return")) {
                throw AgentException(UserFacingErrors.JS_EVAL_FAILED)
            }
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
        if (asProgram && (body == "undefined" || body.isEmpty())) {
            throw AgentException(UserFacingErrors.JS_EVAL_NO_VALUE)
        }
        throw AgentException(UserFacingErrors.JS_EVAL_FAILED)
    }
}
