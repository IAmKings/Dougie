package com.dougie.core.runtime

import com.dougie.core.model.AgentTask
import com.dougie.core.model.CompletionPath
import com.dougie.core.model.ConversationHit
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentMeta
import com.dougie.core.model.MemoryEntry
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object TaskSnapshotCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(task: AgentTask): String = buildJsonObject {
        put("taskId", task.taskId)
        put("input", task.input)
        put("status", task.status.name)
        put("loopCount", task.loopCount)
        put("maxLoops", task.maxLoops)
        put(
            "toolTrace",
            JsonArray(task.toolTrace.map { encodeTrace(it) }),
        )
        putNullable("finalAnswer", task.finalAnswer)
        putNullable("lastError", task.lastError)
        putNullable("streamingText", task.streamingText)
        put(
            "retrievedMemories",
            JsonArray(task.retrievedMemories.map { encodeMemory(it) }),
        )
        put(
            "retrievedConversationHits",
            JsonArray(task.retrievedConversationHits.map { encodeHit(it) }),
        )
        putNullable("attachedCaptureId", task.attachedCaptureId)
        if (task.attachedWidth != null) put("attachedWidth", task.attachedWidth)
        if (task.attachedHeight != null) put("attachedHeight", task.attachedHeight)
        put(
            "attachments",
            JsonArray(task.attachments.map { encodeAttachment(it) }),
        )
        put("speakReply", task.speakReply)
        putNullable("completionPath", task.completionPath?.name)
        if (task.startedAt != null) put("startedAt", task.startedAt)
        if (task.endedAt != null) put("endedAt", task.endedAt)
        if (task.confirmDeadlineAt != null) put("confirmDeadlineAt", task.confirmDeadlineAt)
        put("conversationId", task.conversationId)
    }.toString()

    fun decode(raw: String): AgentTask {
        val obj = json.parseToJsonElement(raw).jsonObject
        return AgentTask(
            taskId = obj.string("taskId"),
            input = obj.string("input"),
            status = TaskStatus.valueOf(obj.string("status")),
            loopCount = obj.int("loopCount"),
            maxLoops = obj["maxLoops"]?.jsonPrimitive?.intOrNull ?: 8,
            toolTrace = obj["toolTrace"]?.jsonArray?.map { decodeTrace(it.jsonObject) }.orEmpty(),
            finalAnswer = obj.optionalString("finalAnswer"),
            lastError = obj.optionalString("lastError"),
            streamingText = obj.optionalString("streamingText"),
            retrievedMemories = obj["retrievedMemories"]?.jsonArray
                ?.map { decodeMemory(it.jsonObject) }
                .orEmpty(),
            retrievedConversationHits = obj["retrievedConversationHits"]?.jsonArray
                ?.map { decodeHit(it.jsonObject) }
                .orEmpty(),
            attachedCaptureId = obj.optionalString("attachedCaptureId"),
            attachedWidth = obj["attachedWidth"]?.jsonPrimitive?.intOrNull,
            attachedHeight = obj["attachedHeight"]?.jsonPrimitive?.intOrNull,
            attachments = obj["attachments"]?.jsonArray
                ?.mapNotNull { decodeAttachment(it.jsonObject) }
                .orEmpty(),
            speakReply = obj.optionalBoolean("speakReply"),
            completionPath = obj.optionalString("completionPath")
                ?.let { runCatching { CompletionPath.valueOf(it) }.getOrNull() },
            startedAt = obj["startedAt"]?.jsonPrimitive?.longOrNull,
            endedAt = obj["endedAt"]?.jsonPrimitive?.longOrNull,
            confirmDeadlineAt = obj["confirmDeadlineAt"]?.jsonPrimitive?.longOrNull,
            conversationId = obj.optionalString("conversationId")
                ?.takeIf { it.isNotBlank() }
                ?: ConversationIds.DEFAULT,
        )
    }

    private fun encodeAttachment(meta: AttachmentMeta): JsonObject = buildJsonObject {
        put("id", meta.id)
        put("kind", meta.kind.name)
        put("width", meta.width)
        put("height", meta.height)
    }

    private fun decodeAttachment(obj: JsonObject): AttachmentMeta? {
        val id = obj.string("id").ifBlank { return null }
        val kind = obj["kind"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { AttachmentKind.valueOf(it) }.getOrNull() }
            ?: return null
        val width = obj["width"]?.jsonPrimitive?.intOrNull ?: return null
        val height = obj["height"]?.jsonPrimitive?.intOrNull ?: return null
        if (width <= 0 || height <= 0) return null
        return AttachmentMeta(id = id, kind = kind, width = width, height = height)
    }

    private fun encodeTrace(entry: ToolTraceEntry): JsonObject = buildJsonObject {
        put("toolCallId", entry.toolCallId)
        put("toolName", entry.toolName)
        put("argsSummary", entry.argsSummary)
        putNullable("resultJson", entry.resultJson)
        put("status", entry.status.name)
        put("riskLevel", entry.riskLevel.name)
    }

    private fun decodeTrace(obj: JsonObject): ToolTraceEntry = ToolTraceEntry(
        toolCallId = obj.string("toolCallId"),
        toolName = obj.string("toolName"),
        argsSummary = obj.string("argsSummary"),
        resultJson = obj.optionalString("resultJson"),
        status = ToolTraceStatus.valueOf(obj.string("status")),
        riskLevel = obj["riskLevel"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { RiskLevel.valueOf(it) }.getOrNull() }
            ?: RiskLevel.L0,
    )

    private fun encodeHit(hit: ConversationHit): JsonObject = buildJsonObject {
        put("taskId", hit.taskId)
        put("conversationId", hit.conversationId)
        put("sourceLabel", hit.sourceLabel)
        put("user", hit.user)
        put("assistant", hit.assistant)
    }

    private fun decodeHit(obj: JsonObject): ConversationHit = ConversationHit(
        taskId = obj.string("taskId"),
        conversationId = obj.optionalString("conversationId")
            ?.takeIf { it.isNotBlank() }
            ?: ConversationIds.DEFAULT,
        sourceLabel = obj.string("sourceLabel"),
        user = obj.string("user"),
        assistant = obj.string("assistant"),
    )

    private fun encodeMemory(entry: MemoryEntry): JsonObject = buildJsonObject {
        put("id", entry.id)
        put("type", entry.type)
        put("content", entry.content)
        put("source", entry.source)
        put("confidence", entry.confidence.toDouble())
        put("createdAt", entry.createdAt)
        put("updatedAt", entry.updatedAt)
    }

    private fun decodeMemory(obj: JsonObject): MemoryEntry = MemoryEntry(
        id = obj.string("id"),
        type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "fact",
        content = obj.string("content"),
        source = obj.string("source"),
        confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f,
        createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
        updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
    )

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.optionalString(key: String): String? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.contentOrNull
    }

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0

    private fun JsonObject.optionalBoolean(key: String): Boolean {
        val value = this[key] ?: return false
        if (value is JsonNull) return false
        return value.jsonPrimitive.booleanOrNull ?: false
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }
}
