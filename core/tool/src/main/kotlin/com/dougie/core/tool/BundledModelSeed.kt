package com.dougie.core.tool

import java.io.File
import java.io.InputStream

fun interface BundledAssetOpen {
    fun open(relativePath: String): InputStream?
}

object BundledModelSeed {
    private val asrFiles = listOf(AsrModelLayout.MODEL_FILE, AsrModelLayout.TOKENS_FILE)
    private val ttsFiles = listOf(
        TtsModelLayout.MODEL_FILE,
        TtsModelLayout.TOKENS_FILE,
        TtsModelLayout.LEXICON_FILE,
    )

    fun seedFromTree(sourceRoot: File, destRoot: File) {
        seed(destRoot) { rel ->
            val file = File(sourceRoot, rel)
            if (file.isFile && file.length() > 0L) file.inputStream() else null
        }
    }

    fun seed(destRoot: File, open: BundledAssetOpen) {
        seedPack(destRoot, AsrModelLayout.DIR, asrFiles, AsrModelLayout::isPresent, open)
        seedPack(destRoot, TtsModelLayout.DIR, ttsFiles, TtsModelLayout::isPresent, open)
    }

    private fun seedPack(
        destRoot: File,
        relativeDir: String,
        files: List<String>,
        alreadyPresent: (File) -> Boolean,
        open: BundledAssetOpen,
    ) {
        val destDir = File(destRoot, relativeDir)
        if (alreadyPresent(destDir)) return
        if (!sourceReady(relativeDir, files, open)) return
        destDir.mkdirs()
        val parts = ArrayList<File>(files.size)
        try {
            for (name in files) {
                val stream = open.open("$relativeDir/$name") ?: run {
                    parts.forEach { it.delete() }
                    return
                }
                val part = File(destDir, "$name.part")
                part.delete()
                stream.use { input ->
                    part.outputStream().use { output -> input.copyTo(output) }
                }
                if (part.length() <= 0L) {
                    part.delete()
                    parts.forEach { it.delete() }
                    return
                }
                parts.add(part)
            }
            for (part in parts) {
                val target = File(destDir, part.name.removeSuffix(".part"))
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
            }
        } catch (t: Throwable) {
            parts.forEach { it.delete() }
            throw t
        }
    }

    private fun sourceReady(
        relativeDir: String,
        files: List<String>,
        open: BundledAssetOpen,
    ): Boolean {
        for (name in files) {
            val stream = open.open("$relativeDir/$name") ?: return false
            val hasByte = stream.use { it.read() >= 0 }
            if (!hasByte) return false
        }
        return true
    }
}
