package com.dougie.core.tool

import java.io.File

object EmbedModelLayout {
    const val ID = "embed"
    const val DIR = "models/embed"
    const val MODEL_FILE = "model.onnx"
    const val VOCAB_FILE = "vocab.txt"

    fun isPresent(modelDir: File): Boolean {
        val model = File(modelDir, MODEL_FILE)
        val vocab = File(modelDir, VOCAB_FILE)
        return model.isFile && model.length() > 0L && vocab.isFile && vocab.length() > 0L
    }
}
