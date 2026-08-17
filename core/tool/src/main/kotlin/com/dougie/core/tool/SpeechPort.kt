package com.dougie.core.tool

interface SpeechPort {
    fun isAppForeground(): Boolean
    fun isModelPresent(): Boolean
    fun isEngineReady(): Boolean
    suspend fun listen(): String
}

class FakeSpeechPort(
    var foreground: Boolean = true,
    var modelPresent: Boolean = true,
    var engineReady: Boolean = true,
    var transcript: String = "现在几点",
) : SpeechPort {
    var listenCount: Int = 0
        private set

    override fun isAppForeground(): Boolean = foreground
    override fun isModelPresent(): Boolean = modelPresent
    override fun isEngineReady(): Boolean = engineReady

    override suspend fun listen(): String {
        listenCount += 1
        return transcript
    }
}
