package com.dougie.core.tool

import java.io.File

object ChatModelLayout {
    const val ID = "chat"
    const val DIR = "models/chat"
    const val MODEL_FILE = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"

    fun isPresent(modelDir: File): Boolean {
        val model = File(modelDir, MODEL_FILE)
        return model.isFile && model.length() > 0L
    }
}
