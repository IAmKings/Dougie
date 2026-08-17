package com.dougie.core.runtime

import com.dougie.core.llm.LlmProvider
import com.dougie.core.model.EgressBlockedException
import com.dougie.core.model.EgressPolicy
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import com.dougie.core.model.MissingApiKeyException

class EgressGateway(
    private val policy: () -> EgressPolicy = { EgressPolicy() },
    private val apiKey: () -> String? = { null },
) {
    suspend fun complete(provider: LlmProvider, context: LoopContext): LlmResponse {
        if (!provider.isLocal && !policy().allowCloud) {
            throw EgressBlockedException()
        }
        if (!provider.isLocal && apiKey().isNullOrBlank()) {
            throw MissingApiKeyException()
        }
        return provider.generate(context)
    }
}
