package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors

object IsolatedPyGuard {
    const val SCRIPT_MAX = IsolatedJsGuard.SCRIPT_MAX
    const val DATA_MAX = IsolatedJsGuard.DATA_MAX

    private val HOST = listOf(
        "fetch(",
        "xmlhttprequest",
        "websocket",
        "java.type",
        "java.",
        "packages.",
        "android.",
        "javax.",
        "kotlin.",
        "urllib",
        "subprocess",
        "socket",
        "ctypes",
    )

    fun assertSize(script: String, dataJson: String) {
        if (script.length > SCRIPT_MAX || dataJson.length > DATA_MAX) {
            throw AgentException(UserFacingErrors.PY_EVAL_TOO_LARGE)
        }
    }

    fun assertNoHost(script: String) {
        val lower = script.lowercase()
        if (HOST.any { lower.contains(it) }) {
            throw AgentException(UserFacingErrors.PY_EVAL_HOST)
        }
    }
}
