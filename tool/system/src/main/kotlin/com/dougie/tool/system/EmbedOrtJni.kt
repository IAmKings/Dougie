package com.dougie.tool.system

import com.dougie.core.tool.EmbedModelLayout
import java.io.File

object EmbedOrtJni {
    fun isAvailable(): Boolean = IntentOrtJni.isAvailable()

    fun embedTokens(modelDir: File, inputIds: LongArray, attentionMask: LongArray): FloatArray? {
        if (!isAvailable()) return null
        if (inputIds.isEmpty() || inputIds.size != attentionMask.size) return null
        return try {
            nativeEmbedTokens(
                File(modelDir, EmbedModelLayout.MODEL_FILE).absolutePath,
                inputIds,
                attentionMask,
            )
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    external fun nativeEmbedTokens(
        modelPath: String,
        inputIds: LongArray,
        attentionMask: LongArray,
    ): FloatArray?
}
