package com.dougie.core.tool

import java.io.File

object EmbedModelLayout {
    const val ID = "embed"
    const val DIR = "models/embed"
    const val MODEL_FILE = "model.onnx"
    const val TOKENIZER_FILE = "tokenizer.json"

    fun isPresent(modelDir: File): Boolean {
        val tokenizer = File(modelDir, TOKENIZER_FILE)
        return tokenizer.isFile && tokenizer.length() > 0L
    }
}
