package com.dougie.app

import android.content.Context
import androidx.compose.runtime.Composable

object ChannelHooks {
    @Suppress("UNUSED_PARAMETER")
    fun hasChannelConsent(context: Context): Boolean = true

    @Suppress("UNUSED_PARAMETER")
    fun seedBundledModels(context: Context) {
    }

    @Composable
    fun Root(content: @Composable () -> Unit) {
        content()
    }
}
