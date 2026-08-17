package com.dougie.app

import java.util.concurrent.ConcurrentHashMap

class PermissionUsageTracker {
    private val times = ConcurrentHashMap<String, Long>()

    fun mark(key: String) {
        times[key] = System.currentTimeMillis()
    }

    fun lastUsedMs(key: String): Long? = times[key]
}
