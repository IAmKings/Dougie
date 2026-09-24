package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.ToolDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream

data class LocalToolContractItem(
    val id: String,
    val text: String,
    val expectTool: String?,
)

/**
 * Frozen local-tool score. [toString] is counts only — never utterance text or tool names.
 */
data class LocalToolContractReport(
    val n: Int,
    val correct: Int,
    val passed: Boolean,
) {
    override fun toString(): String = "local-tool n=$n correct=$correct passed=$passed"
}

object LocalToolContractEval {
    const val EXPECTED_N = 14
    const val RESOURCE = "/eval/local-tool-contract.jsonl"
    const val PREDICTIONS_RELATIVE = "eval/local-tool/predictions.jsonl"
    val ALLOWED_TOOLS = setOf(
        "time",
        "battery",
        "calendar_query",
        "location",
        "clipboard_read",
        "app_intent",
    )

    fun loadResource(): List<LocalToolContractItem> {
        val stream = LocalToolContractEval::class.java.getResourceAsStream(RESOURCE)
            ?: error("missing $RESOURCE")
        return stream.use { load(it) }
    }

    fun load(stream: InputStream): List<LocalToolContractItem> =
        load(stream.bufferedReader().readText())

    fun load(text: String): List<LocalToolContractItem> {
        val seen = HashSet<String>()
        val items = ArrayList<LocalToolContractItem>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val obj = try {
                Json.parseToJsonElement(line).jsonObject
            } catch (_: Exception) {
                error("bad contract line")
            }
            val id = obj["id"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim().orEmpty()
            val utterance = obj["text"]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            if (id.isEmpty() || utterance.isEmpty() || !seen.add(id)) error("bad contract id")
            val expect = when (val el = obj["expectTool"] ?: error("missing expectTool")) {
                JsonNull -> null
                is JsonPrimitive -> {
                    val name = el.contentOrNull?.trim().orEmpty()
                    if (name !in ALLOWED_TOOLS) error("unknown expectTool")
                    name
                }
                else -> error("bad expectTool")
            }
            items += LocalToolContractItem(id, utterance, expect)
        }
        if (items.isEmpty()) error("empty contract")
        return items
    }

    fun score(
        items: List<LocalToolContractItem>,
        predicted: Map<String, String?>,
    ): LocalToolContractReport {
        var correct = 0
        for (item in items) {
            val got = predicted[item.id]?.takeIf { it.isNotBlank() }
            if (got == item.expectTool) correct++
        }
        val passed = items.size == EXPECTED_N && correct == EXPECTED_N
        return LocalToolContractReport(items.size, correct, passed)
    }

    fun protocolActiveFor(items: List<LocalToolContractItem>): Boolean {
        val descriptors = ALLOWED_TOOLS.map { ToolDescriptor(it, description = "contract") }
        return items.all { item ->
            val active = ChatPromptAssembler.localToolProtocolActive(
                AgentTask(taskId = "contract", input = item.text),
                descriptors,
            )
            if (item.expectTool == null) !active else active
        }
    }

    fun writePredictions(file: File, predicted: Map<String, String?>) {
        file.parentFile?.mkdirs()
        val body = predicted.entries.joinToString("\n") { (id, tool) ->
            buildJsonObject {
                put("id", id)
                if (tool.isNullOrBlank()) put("predictedTool", JsonNull) else put("predictedTool", tool)
            }.toString()
        }
        file.writeText(body)
    }

    fun loadPredictions(text: String): Map<String, String?> {
        val out = LinkedHashMap<String, String?>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val obj = Json.parseToJsonElement(line).jsonObject
            val id = obj["id"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim().orEmpty()
            if (id.isEmpty()) continue
            val tool = when (val el = obj["predictedTool"]) {
                null, JsonNull -> null
                is JsonPrimitive -> el.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                else -> null
            }
            out[id] = tool
        }
        return out
    }
}
