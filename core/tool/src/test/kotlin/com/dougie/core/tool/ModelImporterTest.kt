package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelImporterTest {
    private val hello = "hello".toByteArray()
    private val helloSha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    private val world = "world".toByteArray()
    private val worldSha = "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7"

    @Test
    fun wrongHashDoesNotInstall() {
        val dir = Files.createTempDirectory("import-root").toFile()
        try {
            val source = File(dir, "picked.bin").apply { writeBytes(hello) }
            try {
                ModelImporter().importFiles(
                    asrPack(modelSha = "0".repeat(64), tokensSha = worldSha),
                    dir,
                    listOf(source),
                )
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertTrue(e.userMessage.startsWith(UserFacingErrors.MODEL_IMPORT_FAILED))
            }
            assertFalse(AsrModelLayout.isPresent(File(dir, AsrModelLayout.DIR)))
            val packDir = File(dir, AsrModelLayout.DIR)
            assertFalse(File(packDir, AsrModelLayout.MODEL_FILE).exists())
            assertFalse(File(packDir, AsrModelLayout.MODEL_FILE + ".part").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun extraUnmatchedHashDoesNotInstall() {
        val dir = Files.createTempDirectory("import-root").toFile()
        try {
            val model = File(dir, "hf-model.onnx").apply { writeBytes(hello) }
            val tokens = File(dir, "hf-tokens.txt").apply { writeBytes(world) }
            val extra = File(dir, "readme.txt").apply { writeText("extra") }
            try {
                ModelImporter().importFiles(
                    asrPack(modelSha = helloSha, tokensSha = worldSha),
                    dir,
                    listOf(model, tokens, extra),
                )
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.MODEL_IMPORT_FAILED, e.userMessage)
            }
            assertFalse(AsrModelLayout.isPresent(File(dir, AsrModelLayout.DIR)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingFileListsLayoutName() {
        val dir = Files.createTempDirectory("import-root").toFile()
        try {
            val source = File(dir, "model.bin").apply { writeBytes(hello) }
            try {
                ModelImporter().importFiles(
                    asrPack(modelSha = helloSha, tokensSha = worldSha),
                    dir,
                    listOf(source),
                )
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertTrue(e.userMessage.startsWith(UserFacingErrors.MODEL_IMPORT_FAILED))
                assertTrue(e.userMessage.contains(AsrModelLayout.TOKENS_FILE))
            }
            assertFalse(AsrModelLayout.isPresent(File(dir, AsrModelLayout.DIR)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun successMatchesAsrLayoutWithoutHttps() {
        val dir = Files.createTempDirectory("import-root").toFile()
        try {
            val model = File(dir, "hf-model.onnx").apply { writeBytes(hello) }
            val tokens = File(dir, "hf-tokens.txt").apply { writeBytes(world) }
            ModelImporter().importFiles(
                asrPack(modelSha = helloSha, tokensSha = worldSha),
                dir,
                listOf(model, tokens),
            )
            assertTrue(AsrModelLayout.isPresent(File(dir, AsrModelLayout.DIR)))
            assertEquals(hello.toList(), File(dir, "${AsrModelLayout.DIR}/${AsrModelLayout.MODEL_FILE}").readBytes().toList())
            assertEquals(world.toList(), File(dir, "${AsrModelLayout.DIR}/${AsrModelLayout.TOKENS_FILE}").readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun intentPackWritesOnnxLayout() {
        val dir = Files.createTempDirectory("import-root").toFile()
        try {
            val onnx = File(dir, "intent.onnx").apply { writeBytes(hello) }
            val tokenizer = File(dir, "tok.json").apply { writeBytes(world) }
            val labels = File(dir, "labs.txt").apply { writeText("query_time") }
            val vocab = File(dir, "vocab.txt").apply { writeText("[PAD]\n") }
            val labelsSha = SHA256.hex(labels)
            val vocabSha = SHA256.hex(vocab)
            val pack = ModelPack(
                id = IntentModelLayout.ID,
                relativeDir = IntentModelLayout.DIR,
                files = listOf(
                    ModelFileSpec(IntentModelLayout.MODEL_FILE, helloSha, httpsUrl = ""),
                    ModelFileSpec(IntentModelLayout.TOKENIZER_FILE, worldSha, httpsUrl = ""),
                    ModelFileSpec(IntentModelLayout.LABELS_FILE, labelsSha, httpsUrl = ""),
                    ModelFileSpec(IntentModelLayout.VOCAB_FILE, vocabSha, httpsUrl = ""),
                ),
            )
            ModelImporter().importFiles(pack, dir, listOf(onnx, tokenizer, labels, vocab))
            val packDir = File(dir, IntentModelLayout.DIR)
            assertTrue(IntentModelLayout.isPresent(packDir))
            assertFalse(File(packDir, "model.gguf").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun asrPack(modelSha: String, tokensSha: String) = ModelPack(
        id = "asr",
        relativeDir = AsrModelLayout.DIR,
        files = listOf(
            ModelFileSpec(AsrModelLayout.MODEL_FILE, modelSha, httpsUrl = ""),
            ModelFileSpec(AsrModelLayout.TOKENS_FILE, tokensSha, httpsUrl = ""),
        ),
    )
}
