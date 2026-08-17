package com.dougie.core.tool

interface ClipboardPort {
    fun isAppForeground(): Boolean

    fun readText(): String?

    fun writeText(text: String)
}

class FakeClipboardPort(
    var foreground: Boolean = true,
    var text: String? = null,
) : ClipboardPort {
    var writeCount: Int = 0
        private set

    override fun isAppForeground(): Boolean = foreground

    override fun readText(): String? = text

    override fun writeText(text: String) {
        writeCount += 1
        this.text = text
    }
}
