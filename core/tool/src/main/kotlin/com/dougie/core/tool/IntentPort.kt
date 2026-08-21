package com.dougie.core.tool

import java.io.File

object IntentModelLayout {
    const val DIR = "models/intent"
    const val MODEL_FILE = "model.onnx"
    const val TOKENIZER_FILE = "tokenizer.json"
    const val LABELS_FILE = "labels.txt"
    const val ID = "intent"
    const val MIN_CONFIDENCE = 0.5

    fun isPresent(modelDir: File): Boolean {
        return listOf(MODEL_FILE, TOKENIZER_FILE, LABELS_FILE).all { name ->
            val file = File(modelDir, name)
            file.isFile && file.length() > 0L
        }
    }
}

data class IntentHit(
    val intent: String,
    val slots: Map<String, String> = emptyMap(),
    val route: String,
    val confidence: Double,
)

interface IntentEngine {
    fun isReady(): Boolean
    suspend fun classify(text: String): IntentHit
}

object UnwiredIntentEngine : IntentEngine {
    override fun isReady(): Boolean = false
    override suspend fun classify(text: String): IntentHit {
        error("offline intent engine is not wired")
    }
}

class FakeIntentEngine(
    var ready: Boolean = true,
    var hit: IntentHit = IntentHit(
        intent = "query_time",
        slots = emptyMap(),
        route = "time",
        confidence = 0.9,
    ),
) : IntentEngine {
    val classified = mutableListOf<String>()

    override fun isReady(): Boolean = ready

    override suspend fun classify(text: String): IntentHit {
        classified += text
        return hit
    }
}

interface IntentPort {
    fun isModelPresent(): Boolean
    fun isEngineReady(): Boolean
    suspend fun classify(text: String): IntentHit
}

class FakeIntentPort(
    var modelPresent: Boolean = true,
    var engineReady: Boolean = true,
    var hit: IntentHit = IntentHit(
        intent = "query_time",
        slots = emptyMap(),
        route = "time",
        confidence = 0.9,
    ),
) : IntentPort {
    var classifyCount: Int = 0
        private set

    override fun isModelPresent(): Boolean = modelPresent
    override fun isEngineReady(): Boolean = engineReady

    override suspend fun classify(text: String): IntentHit {
        classifyCount += 1
        return hit
    }
}
