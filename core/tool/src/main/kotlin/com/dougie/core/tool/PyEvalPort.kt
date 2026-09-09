package com.dougie.core.tool

interface PyEvalPort {
    fun isReady(): Boolean
    /** Returns JSON text of the last expression (not wrapped). */
    fun evaluate(script: String, dataJson: String): String
}
