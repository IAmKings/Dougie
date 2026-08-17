package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BundledModelSeedTest {
    @Test
    fun copiesCompleteAsrAndTtsLayouts() {
        val root = Files.createTempDirectory("bundled-seed").toFile()
        try {
            val source = root.resolve("src")
            val dest = root.resolve("dst")
            writeAsr(source, "asr-model", "asr-tokens")
            writeTts(source, "tts-model", "tts-tokens", "tts-lex")
            BundledModelSeed.seedFromTree(source, dest)
            assertTrue(AsrModelLayout.isPresent(dest.resolve(AsrModelLayout.DIR)))
            assertTrue(TtsModelLayout.isPresent(dest.resolve(TtsModelLayout.DIR)))
            assertEquals("asr-model", dest.resolve(AsrModelLayout.DIR).resolve(AsrModelLayout.MODEL_FILE).readText())
            assertEquals("tts-lex", dest.resolve(TtsModelLayout.DIR).resolve(TtsModelLayout.LEXICON_FILE).readText())
            assertFalse(dest.resolve("models/intent").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun skipsWhenLayoutAlreadyPresent() {
        val root = Files.createTempDirectory("bundled-skip").toFile()
        try {
            val source = root.resolve("src")
            val dest = root.resolve("dst")
            writeAsr(source, "new-model", "new-tokens")
            writeAsr(dest, "old-model", "old-tokens")
            BundledModelSeed.seedFromTree(source, dest)
            assertEquals("old-model", dest.resolve(AsrModelLayout.DIR).resolve(AsrModelLayout.MODEL_FILE).readText())
            assertEquals("old-tokens", dest.resolve(AsrModelLayout.DIR).resolve(AsrModelLayout.TOKENS_FILE).readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun emptySourceFileDoesNotPretendSuccess() {
        val root = Files.createTempDirectory("bundled-empty").toFile()
        try {
            val source = root.resolve("src")
            val dest = root.resolve("dst")
            writeAsr(source, "", "tokens")
            writeTts(source, "tts-model", "tts-tokens", "tts-lex")
            BundledModelSeed.seedFromTree(source, dest)
            assertFalse(AsrModelLayout.isPresent(dest.resolve(AsrModelLayout.DIR)))
            assertFalse(dest.resolve(AsrModelLayout.DIR).resolve(AsrModelLayout.MODEL_FILE).exists())
            assertTrue(TtsModelLayout.isPresent(dest.resolve(TtsModelLayout.DIR)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun incompleteSourceDoesNotPretendSuccess() {
        val root = Files.createTempDirectory("bundled-incomplete").toFile()
        try {
            val source = root.resolve("src")
            val dest = root.resolve("dst")
            val asrSrc = source.resolve(AsrModelLayout.DIR)
            asrSrc.mkdirs()
            asrSrc.resolve(AsrModelLayout.MODEL_FILE).writeText("only-model")
            BundledModelSeed.seedFromTree(source, dest)
            val asrDest = dest.resolve(AsrModelLayout.DIR)
            assertFalse(AsrModelLayout.isPresent(asrDest))
            assertFalse(asrDest.resolve(AsrModelLayout.MODEL_FILE).exists())
            assertFalse(asrDest.resolve(AsrModelLayout.MODEL_FILE + ".part").exists())
            assertFalse(TtsModelLayout.isPresent(dest.resolve(TtsModelLayout.DIR)))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeAsr(root: java.io.File, model: String, tokens: String) {
        val dir = root.resolve(AsrModelLayout.DIR)
        dir.mkdirs()
        dir.resolve(AsrModelLayout.MODEL_FILE).writeText(model)
        dir.resolve(AsrModelLayout.TOKENS_FILE).writeText(tokens)
    }

    private fun writeTts(root: java.io.File, model: String, tokens: String, lexicon: String) {
        val dir = root.resolve(TtsModelLayout.DIR)
        dir.mkdirs()
        dir.resolve(TtsModelLayout.MODEL_FILE).writeText(model)
        dir.resolve(TtsModelLayout.TOKENS_FILE).writeText(tokens)
        dir.resolve(TtsModelLayout.LEXICON_FILE).writeText(lexicon)
    }
}
