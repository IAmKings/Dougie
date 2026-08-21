package com.dougie.tool.system

import android.content.Context
import com.dougie.core.tool.IntentEngine
import com.dougie.core.tool.IntentHit
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.IntentPort
import com.dougie.core.tool.OnnxIntentEngine
import java.io.File

class AndroidIntentPort(
    context: Context,
    engine: IntentEngine? = null,
) : IntentPort {
    private val modelDir = File(context.applicationContext.filesDir, IntentModelLayout.DIR)
    private val resolved = engine ?: OnnxIntentEngine(
        modelDir = modelDir,
        nativeAvailable = { IntentOrtJni.isAvailable() },
        infer = { dir, features -> IntentOrtJni.infer(dir, features) },
    )

    override fun isModelPresent(): Boolean = IntentModelLayout.isPresent(modelDir)

    override fun isEngineReady(): Boolean = resolved.isReady()

    override suspend fun classify(text: String): IntentHit = resolved.classify(text)
}
