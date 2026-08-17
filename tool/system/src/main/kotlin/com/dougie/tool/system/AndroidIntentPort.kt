package com.dougie.tool.system

import android.content.Context
import com.dougie.core.tool.IntentEngine
import com.dougie.core.tool.IntentHit
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.IntentPort
import com.dougie.core.tool.UnwiredIntentEngine
import java.io.File

class AndroidIntentPort(
    context: Context,
    private val engine: IntentEngine = UnwiredIntentEngine,
) : IntentPort {
    private val modelDir = File(context.applicationContext.filesDir, IntentModelLayout.DIR)

    override fun isModelPresent(): Boolean = IntentModelLayout.isPresent(modelDir)

    override fun isEngineReady(): Boolean = engine.isReady()

    override suspend fun classify(text: String): IntentHit = engine.classify(text)
}
