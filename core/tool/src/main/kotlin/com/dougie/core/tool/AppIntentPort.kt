package com.dougie.core.tool

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface AppIntentPort {
    fun isAppForeground(): Boolean

    fun launchView(uri: String, packageName: String?): String
}

class FakeAppIntentPort(
    var foreground: Boolean = true,
) : AppIntentPort {
    val launches = mutableListOf<Launch>()

    val launchCount: Int get() = launches.size

    override fun isAppForeground(): Boolean = foreground

    override fun launchView(uri: String, packageName: String?): String {
        launches += Launch(uri = uri, packageName = packageName)
        return buildJsonObject {
            put("ok", true)
            put("launched", uri)
        }.toString()
    }

    data class Launch(val uri: String, val packageName: String?)
}
