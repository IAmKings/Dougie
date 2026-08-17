package com.dougie.core.runtime

import com.dougie.core.llm.LlmProvider
import com.dougie.core.llm.toLlmResponse
import com.dougie.core.model.EgressBlockedException
import com.dougie.core.model.EgressPolicy
import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.MissingApiKeyException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class EgressGateway(
    private val policy: () -> EgressPolicy = { EgressPolicy() },
    private val apiKey: () -> String? = { null },
) {
    fun stream(provider: LlmProvider, context: LoopContext): Flow<LlmEvent> = flow {
        ensureAllowed(provider)
        emitAll(provider.stream(context))
    }

    suspend fun complete(provider: LlmProvider, context: LoopContext): LlmResponse {
        return stream(provider, context).toLlmResponse()
    }

    private fun ensureAllowed(provider: LlmProvider) {
        if (!provider.isLocal && !policy().allowCloud) {
            throw EgressBlockedException()
        }
        if (!provider.isLocal && apiKey().isNullOrBlank()) {
            throw MissingApiKeyException()
        }
    }
}
