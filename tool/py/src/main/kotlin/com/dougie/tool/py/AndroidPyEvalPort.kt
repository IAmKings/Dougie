package com.dougie.tool.py

import android.content.Context
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.PyEvalPort
import com.dougie.core.tool.PyEvalTool
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AndroidPyEvalPort(context: Context) : PyEvalPort {
    private val app = context.applicationContext
    private val exec = Executors.newSingleThreadExecutor()

    override fun isReady(): Boolean = true

    override fun evaluate(script: String, dataJson: String): String {
        Json.parseToJsonElement(dataJson)
        val future = exec.submit<String> {
            startIfNeeded()
            val py = try {
                Python.getInstance()
            } catch (_: Throwable) {
                throw AgentException(UserFacingErrors.PY_ENGINE_NOT_READY)
            }
            try {
                val raw = py.getModule("py_eval_runtime")
                    .callAttr("evaluate", script, dataJson, sandboxRoot())
                raw.toJava(String::class.java) as String
            } catch (e: PyException) {
                throw mapPy(e)
            }
        }
        return try {
            future.get(PyEvalTool.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            throw AgentException(UserFacingErrors.PY_EVAL_TIMEOUT)
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is AgentException -> throw cause
                else -> throw AgentException(UserFacingErrors.PY_EVAL_FAILED)
            }
        }
    }

    private fun sandboxRoot(): String {
        val dir = File(app.filesDir, "py_sandbox")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.canonicalFile.absolutePath
    }

    private fun startIfNeeded() {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(app))
            }
        } catch (_: Throwable) {
            throw AgentException(UserFacingErrors.PY_ENGINE_NOT_READY)
        }
    }

    private fun mapPy(error: PyException): AgentException {
        val text = error.message.orEmpty()
        return when {
            text.contains("quota") -> AgentException(UserFacingErrors.PY_EVAL_QUOTA)
            text.contains("host") -> AgentException(UserFacingErrors.PY_EVAL_HOST)
            text.contains("no_value") -> AgentException(UserFacingErrors.PY_EVAL_NO_VALUE)
            text.contains("engine") -> AgentException(UserFacingErrors.PY_ENGINE_NOT_READY)
            else -> AgentException(UserFacingErrors.PY_EVAL_FAILED)
        }
    }
}
