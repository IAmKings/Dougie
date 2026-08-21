package com.dougie.tool.system

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.IntentModelLayout
import java.io.File

object IntentOrtJni {
    @Volatile
    private var loaded: Boolean? = null

    fun isAvailable(): Boolean {
        val cached = loaded
        if (cached != null) return cached
        val ok = runCatching {
            runCatching { System.loadLibrary("onnxruntime") }
            System.loadLibrary("dougie_intent")
        }.isSuccess
        loaded = ok
        return ok
    }

    fun infer(modelDir: File, features: FloatArray): FloatArray {
        if (!isAvailable()) {
            throw AgentException(UserFacingErrors.INTENT_ENGINE_NOT_READY)
        }
        if (features.isEmpty()) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        return try {
            nativeInfer(File(modelDir, IntentModelLayout.MODEL_FILE).absolutePath, features)
                ?: throw AgentException(UserFacingErrors.INTENT_FAILED)
        } catch (e: AgentException) {
            throw e
        } catch (_: Throwable) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
    }

    @JvmStatic
    external fun nativeInfer(modelPath: String, features: FloatArray): FloatArray?
}
