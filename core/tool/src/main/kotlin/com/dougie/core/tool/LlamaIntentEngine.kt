package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class LlamaIntentEngine(
    private val modelDir: File,
    private val nativeAvailable: () -> Boolean,
    private val complete: (File, String) -> String,
) : IntentEngine {
    override fun isReady(): Boolean = IntentModelLayout.isPresent(modelDir) && nativeAvailable()

    override suspend fun classify(text: String): IntentHit {
        if (!isReady()) {
            throw AgentException(UserFacingErrors.INTENT_ENGINE_NOT_READY)
        }
        val raw = try {
            complete(modelDir, IntentPrompt.render(text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            throw e
        } catch (_: Exception) {
            throw AgentException(UserFacingErrors.INTENT_FAILED)
        }
        return IntentJsonParser.parse(raw)
    }
}
