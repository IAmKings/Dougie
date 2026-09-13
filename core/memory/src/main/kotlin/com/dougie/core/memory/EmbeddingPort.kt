package com.dougie.core.memory

interface EmbeddingPort {
    fun isReady(): Boolean

    /** Null means skip semantic recall. Do not log [text]. */
    suspend fun embed(text: String): FloatArray?

    /** Search queries may add a model-specific prefix. Facts stay identity. */
    fun prepareQuery(text: String): String = text

    fun preparePassage(text: String): String = text
}
