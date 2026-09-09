package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** JVM test double: program mode; last expression is the result. */
class FakePyEvalPort(
    var ready: Boolean = true,
    var failWith: String? = null,
) : PyEvalPort {
    var lastScript: String = ""
    var lastData: String = ""

    override fun isReady(): Boolean = ready

    override fun evaluate(script: String, dataJson: String): String {
        lastScript = script
        lastData = dataJson
        failWith?.let { throw AgentException(it) }
        val body = script.trim().removeSuffix(";").trim()
        if (body.isEmpty() || body == "None" || body == "NoneType") {
            throw AgentException(UserFacingErrors.PY_EVAL_NO_VALUE)
        }
        if (body == "data" || body.endsWith("\ndata") || body == "return data") {
            return dataJson
        }
        if (body.contains("np.array(data).sum") || body.contains("numpy")) {
            val el = Json.parseToJsonElement(dataJson) as? JsonArray
                ?: throw AgentException(UserFacingErrors.PY_EVAL_FAILED)
            var sum = 0.0
            for (item in el) {
                val n = item.jsonPrimitive.doubleOrNull
                    ?: throw AgentException(UserFacingErrors.PY_EVAL_FAILED)
                sum += n
            }
            return if (sum == sum.toLong().toDouble()) {
                sum.toLong().toString()
            } else {
                sum.toString()
            }
        }
        throw AgentException(UserFacingErrors.PY_EVAL_FAILED)
    }
}
