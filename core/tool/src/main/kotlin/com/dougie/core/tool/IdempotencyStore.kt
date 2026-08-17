package com.dougie.core.tool

import java.util.concurrent.ConcurrentHashMap

interface IdempotencyStore {
    fun get(key: String): String?
    fun put(key: String, resultJson: String)
}

class InMemoryIdempotencyStore : IdempotencyStore {
    private val values = ConcurrentHashMap<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, resultJson: String) {
        values.putIfAbsent(key, resultJson)
    }
}
