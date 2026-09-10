package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors

object PhoneNumber {
    fun canonical(raw: String): String {
        val stripped = raw.trim().replace(" ", "").replace("-", "")
        val plus = stripped.startsWith("+")
        val digits = if (plus) stripped.drop(1) else stripped
        if (digits.isEmpty() || digits.any { !it.isDigit() } || digits.length !in 8..20) {
            throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
        }
        return if (plus) "+$digits" else digits
    }
}
