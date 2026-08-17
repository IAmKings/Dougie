package com.dougie.tool.system

import android.content.Context
import com.dougie.core.tool.IntentEngine
import com.dougie.core.tool.IntentHit
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.IntentPort
import com.dougie.core.tool.LlamaIntentEngine
import java.io.File

class AndroidIntentPort(
    context: Context,
    engine: IntentEngine? = null,
) : IntentPort {
    private val modelDir = File(context.applicationContext.filesDir, IntentModelLayout.DIR)
    private val resolved = engine ?: LlamaIntentEngine(
        modelDir = modelDir,
        nativeAvailable = { LlamaJni.isAvailable() },
        complete = { dir, prompt -> LlamaJni.complete(dir, prompt) },
    )

    override fun isModelPresent(): Boolean = IntentModelLayout.isPresent(modelDir)

    override fun isEngineReady(): Boolean = resolved.isReady()

    override suspend fun classify(text: String): IntentHit = resolved.classify(text)
}
