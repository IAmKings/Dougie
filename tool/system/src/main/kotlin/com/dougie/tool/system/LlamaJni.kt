package com.dougie.tool.system

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.IntentModelLayout
import java.io.File

object LlamaJni {
    @Volatile
    private var loaded: Boolean? = null

    fun isAvailable(): Boolean {
        val cached = loaded
        if (cached != null) return cached
        val ok = runCatching { System.loadLibrary("llama") }.isSuccess
        loaded = ok
        return ok
    }

    fun complete(modelDir: File, prompt: String): String {
        if (!isAvailable()) {
            throw AgentException(UserFacingErrors.INTENT_ENGINE_NOT_READY)
        }
        return try {
            nativeComplete(File(modelDir, IntentModelLayout.MODEL_FILE).absolutePath, prompt)
        } catch (_: Throwable) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
    }

    @JvmStatic
    external fun nativeComplete(modelPath: String, prompt: String): String
}
