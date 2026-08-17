package com.dougie.core.llm

import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext

interface LlmProvider {
    val isLocal: Boolean
        get() = false

    suspend fun generate(context: LoopContext): LlmResponse
}
