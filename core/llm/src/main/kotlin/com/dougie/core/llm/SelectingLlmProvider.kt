package com.dougie.core.llm

import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import kotlinx.coroutines.flow.Flow

/**
 * Picks cloud when configured; otherwise local if ready; otherwise cloud so
 * EgressGateway can still emit EGRESS_BLOCKED / MISSING_API_KEY.
 */
class SelectingLlmProvider(
    private val cloud: LlmProvider,
    private val local: LlmProvider?,
    private val cloudConfigured: () -> Boolean,
    private val localReady: () -> Boolean,
) : LlmProvider {
    override val isLocal: Boolean
        get() = !cloudConfigured() && local != null && localReady()

    /** Cloud configured or a ready local chat pack — MiniRBT shortcut should not run. */
    val hasConversationalLlm: Boolean
        get() = cloudConfigured() || (local != null && localReady())

    override fun stream(context: LoopContext): Flow<LlmEvent> = active().stream(context)

    override suspend fun generate(context: LoopContext): LlmResponse = active().generate(context)

    private fun active(): LlmProvider {
        if (cloudConfigured()) return cloud
        val localProvider = local
        if (localProvider != null && localReady()) return localProvider
        return cloud
    }
}
