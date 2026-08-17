package com.dougie.app

import android.content.Context
import androidx.compose.runtime.Composable

object ChannelHooks {
    @Suppress("UNUSED_PARAMETER")
    fun hasChannelConsent(context: Context): Boolean = true

    @Composable
    fun Root(content: @Composable () -> Unit) {
        content()
    }
}
