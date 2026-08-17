package com.dougie.core.tool

interface CalendarPort {
    suspend fun queryUpcoming(limit: Int): String

    suspend fun createEvent(title: String, startIso: String, idempotencyKey: String): String
}

class FakeCalendarPort(
    private val queryJson: String = """{"events":[]}""",
) : CalendarPort {
    val createCalls = mutableListOf<CreateCall>()
    private val created = LinkedHashMap<String, String>()

    override suspend fun queryUpcoming(limit: Int): String = queryJson

    override suspend fun createEvent(title: String, startIso: String, idempotencyKey: String): String {
        created[idempotencyKey]?.let { return it }
        createCalls += CreateCall(title, startIso, idempotencyKey)
        val json = """{"ok":true,"id":"${created.size + 1}","title":"$title"}"""
        created[idempotencyKey] = json
        return json
    }

    data class CreateCall(
        val title: String,
        val startIso: String,
        val idempotencyKey: String,
    )
}
