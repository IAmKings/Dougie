package com.dougie.core.tool

import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface TelecomPort {
    fun isAppForeground(): Boolean

    fun composeSms(to: String, body: String): String

    fun dial(number: String): String
}

class FakeTelecomPort(
    var foreground: Boolean = true,
    var failLaunch: Boolean = false,
) : TelecomPort {
    var lastCompose: Compose? = null
        private set
    var lastDial: String? = null
        private set
    var composeCount: Int = 0
        private set
    var dialCount: Int = 0
        private set

    override fun isAppForeground(): Boolean = foreground

    override fun composeSms(to: String, body: String): String {
        if (!foreground) return failJson(UserFacingErrors.APP_INTENT_NOT_FOREGROUND)
        if (failLaunch) return failJson(UserFacingErrors.TELECOM_LAUNCH_FAILED)
        lastCompose = Compose(to = to, body = body)
        composeCount += 1
        return okJson()
    }

    override fun dial(number: String): String {
        if (!foreground) return failJson(UserFacingErrors.APP_INTENT_NOT_FOREGROUND)
        if (failLaunch) return failJson(UserFacingErrors.TELECOM_LAUNCH_FAILED)
        lastDial = number
        dialCount += 1
        return okJson()
    }

    data class Compose(val to: String, val body: String)

    private fun okJson(): String = buildJsonObject { put("ok", true) }.toString()

    private fun failJson(message: String): String =
        buildJsonObject {
            put("ok", false)
            put("error", message)
        }.toString()
}
