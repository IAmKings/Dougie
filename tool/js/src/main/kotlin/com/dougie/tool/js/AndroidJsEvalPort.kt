package com.dougie.tool.js

import app.cash.quickjs.QuickJs
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.IsolatedJsGuard
import com.dougie.core.tool.JsEvalPort
import com.dougie.core.tool.JsEvalTool
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AndroidJsEvalPort : JsEvalPort {
    override fun isReady(): Boolean = true

    override fun evaluate(script: String, dataJson: String, asProgram: Boolean): String {
        Json.parseToJsonElement(dataJson)
        val program = IsolatedJsGuard.wrapForExecute(script, dataJson, asProgram)
        val exec = Executors.newSingleThreadExecutor()
        try {
            val future = exec.submit<String> {
                val quickJs = try {
                    QuickJs.create()
                } catch (_: Throwable) {
                    throw AgentException(UserFacingErrors.JS_ENGINE_NOT_READY)
                }
                try {
                    val raw = quickJs.evaluate(program)
                        ?: throw AgentException(
                            if (asProgram) UserFacingErrors.JS_EVAL_NO_VALUE
                            else UserFacingErrors.JS_EVAL_FAILED,
                        )
                    raw.toString()
                } finally {
                    try {
                        quickJs.close()
                    } catch (_: Throwable) {
                    }
                }
            }
            return try {
                future.get(JsEvalTool.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                future.cancel(true)
                throw AgentException(UserFacingErrors.JS_EVAL_TIMEOUT)
            } catch (e: ExecutionException) {
                when (val cause = e.cause) {
                    is AgentException -> throw cause
                    else -> throw AgentException(UserFacingErrors.JS_EVAL_FAILED)
                }
            }
        } finally {
            exec.shutdownNow()
        }
    }
}
