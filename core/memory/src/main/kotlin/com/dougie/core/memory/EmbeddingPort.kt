package com.dougie.core.memory

interface EmbeddingPort {
    fun isReady(): Boolean

    /** Null means skip semantic recall. Do not log [text]. */
    suspend fun embed(text: String): FloatArray?
}
