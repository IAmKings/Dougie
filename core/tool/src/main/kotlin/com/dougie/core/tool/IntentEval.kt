package com.dougie.core.tool

import com.dougie.core.model.AgentException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class IntentEvalItem(
    val id: String,
    val goldIntent: String,
    val modelJson: String,
)

data class IntentEvalReport(
    val total: Int,
    val correct: Int,
    val accuracy: Double,
    val passed: Boolean,
) {
    override fun toString(): String =
        "intent accuracy=$accuracy correct=$correct/$total passed=$passed (threshold 0.90)"
}

object IntentEval {
    const val THRESHOLD = 0.90

    fun loadItems(json: String): List<IntentEvalItem> {
        val root = Json.parseToJsonElement(json).jsonObject
        return root.getValue("items").jsonArray.map { el ->
            val obj = el.jsonObject
            IntentEvalItem(
                id = obj.getValue("id").jsonPrimitive.content,
                goldIntent = obj.getValue("goldIntent").jsonPrimitive.content,
                modelJson = obj.getValue("modelJson").jsonPrimitive.content,
            )
        }
    }

    fun report(items: List<IntentEvalItem>): IntentEvalReport {
        require(items.isNotEmpty())
        val correct = items.count { item ->
            try {
                IntentJsonParser.parse(item.modelJson).intent == item.goldIntent
            } catch (_: AgentException) {
                false
            }
        }
        val accuracy = correct.toDouble() / items.size
        return IntentEvalReport(
            total = items.size,
            correct = correct,
            accuracy = accuracy,
            passed = accuracy >= THRESHOLD,
        )
    }
}
