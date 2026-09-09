package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** JVM test double: program mode; last expression is the result. */
class FakePyEvalPort(
    var ready: Boolean = true,
    var failWith: String? = null,
    val sandboxDir: File? = null,
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
        rejectEscapingPath(body)
        csvRoundTrip(body)?.let { return it }
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

    private fun rejectEscapingPath(script: String) {
        if (script.contains("../") || script.contains("..\\")) {
            throw AgentException(UserFacingErrors.PY_EVAL_HOST)
        }
        if (script.contains("://") ||
            script.contains("content:", ignoreCase = true) ||
            FILE_SCHEME.containsMatchIn(script)
        ) {
            throw AgentException(UserFacingErrors.PY_EVAL_HOST)
        }
        if (OPEN_ABS.containsMatchIn(script) || IO_OPEN_ABS.containsMatchIn(script)) {
            throw AgentException(UserFacingErrors.PY_EVAL_HOST)
        }
    }

    private fun csvRoundTrip(script: String): String? {
        if (!script.contains("t.csv")) return null
        val dir = sandboxDir ?: throw AgentException(UserFacingErrors.PY_EVAL_FAILED)
        dir.mkdirs()
        val file = File(dir, "t.csv")
        val writing = script.contains("'w'") || script.contains("\"w\"")
        if (writing) {
            val payload = "1\n2\n"
            val incoming = payload.toByteArray().size.toLong()
            val used = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (used + incoming > QUOTA_BYTES) {
                throw AgentException(UserFacingErrors.PY_EVAL_QUOTA)
            }
            file.writeText(payload)
        }
        if (!file.exists()) {
            throw AgentException(UserFacingErrors.PY_EVAL_FAILED)
        }
        val sum = file.readLines().mapNotNull { it.trim().toDoubleOrNull() }.sum()
        return if (sum == sum.toLong().toDouble()) {
            sum.toLong().toString()
        } else {
            sum.toString()
        }
    }

    companion object {
        const val QUOTA_BYTES = 32L * 1024 * 1024
        private val OPEN_ABS = Regex("""open\(\s*['\"]/""")
        private val IO_OPEN_ABS = Regex("""io\.open\(\s*['\"]/""")
        private val FILE_SCHEME = Regex("""['\"]file:""")
    }
}
