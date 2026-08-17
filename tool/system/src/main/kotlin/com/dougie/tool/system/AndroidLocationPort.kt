package com.dougie.tool.system

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.dougie.core.tool.LocationPort
import java.util.Locale
import kotlin.math.roundToInt

class AndroidLocationPort(
    context: Context,
    private val onUsed: () -> Unit = {},
) : LocationPort {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    override suspend fun lastKnownCoarse(): String {
        onUsed()
        val location = lastKnown()
        if (location == null) {
            return """{"ok":false,"error":"unavailable"}"""
        }
        val provider = location.provider
        val providerJson = if (provider == null) {
            "null"
        } else {
            "\"${provider.replace("\\", "").replace("\"", "")}\""
        }
        return """{"ok":true,"latitude":${coarse(location.latitude)},"longitude":${coarse(location.longitude)},"accuracy_m":${location.accuracy},"provider":$providerJson}"""
    }

    private fun lastKnown(): Location? {
        if (manager == null) return null
        val network = runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        val passive = runCatching { manager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
        return listOfNotNull(network, passive).maxByOrNull { it.time }
    }

    private fun coarse(value: Double): String {
        val rounded = (value * 100.0).roundToInt() / 100.0
        return String.format(Locale.US, "%.2f", rounded)
    }
}
