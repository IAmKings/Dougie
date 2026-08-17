package com.dougie.tool.accessibility

interface GesturePort {
    fun isConnected(): Boolean
    fun foregroundPackage(): String?
    suspend fun tap(x: Int, y: Int): Boolean
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean
}
