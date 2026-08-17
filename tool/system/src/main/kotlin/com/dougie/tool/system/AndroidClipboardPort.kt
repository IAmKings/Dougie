package com.dougie.tool.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.dougie.core.tool.ClipboardPort

class AndroidClipboardPort(
    context: Context,
    private val isForeground: () -> Boolean,
    private val onUsed: () -> Unit = {},
) : ClipboardPort {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)

    override fun isAppForeground(): Boolean = isForeground()

    override fun readText(): String? {
        onUsed()
        val clip = clipboard?.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(appContext)?.toString()
    }

    override fun writeText(text: String) {
        onUsed()
        clipboard?.setPrimaryClip(ClipData.newPlainText("Dougie", text))
    }
}
