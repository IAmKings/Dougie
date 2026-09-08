package com.dougie.core.tool

interface JsEvalPort {
    fun isReady(): Boolean
    /** Returns JSON text of the script result (not wrapped). */
    fun evaluate(script: String, dataJson: String, asProgram: Boolean = false): String
}
