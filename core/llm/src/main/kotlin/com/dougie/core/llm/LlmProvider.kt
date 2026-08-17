package com.dougie.core.llm

import com.dougie.core.model.LlmEvent
import com.dougie.core.model.LlmResponse
import com.dougie.core.model.LoopContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface LlmProvider {
    val isLocal: Boolean
        get() = false

    fun stream(context: LoopContext): Flow<LlmEvent> = flow {
        when (val response = generate(context)) {
            is LlmResponse.FinalAnswer -> emit(LlmEvent.TextDelta(response.text))
            is LlmResponse.ToolCall -> emit(
                LlmEvent.ToolCall(id = response.id, name = response.name, argsJson = response.argsJson),
            )
        }
    }

    suspend fun generate(context: LoopContext): LlmResponse
}

suspend fun Flow<LlmEvent>.toLlmResponse(): LlmResponse {
    val text = StringBuilder()
    var tool: LlmEvent.ToolCall? = null
    collect { event ->
        when (event) {
            is LlmEvent.TextDelta -> text.append(event.text)
            is LlmEvent.ToolCall -> tool = event
        }
    }
    val completed = tool
    return if (completed != null) {
        LlmResponse.ToolCall(id = completed.id, name = completed.name, argsJson = completed.argsJson)
    } else {
        LlmResponse.FinalAnswer(text.toString())
    }
}
