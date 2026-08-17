package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelInstallerTest {
    private val hello = "hello".toByteArray()
    private val helloSha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

    @Test
    fun unconfirmedDoesNotFetch() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, _, _ -> fetches += 1 }
        val dir = Files.createTempDirectory("model-root").toFile()
        try {
            try {
                installer.install(pack(), dir, userConfirmed = false)
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.MODEL_DOWNLOAD_DENIED, e.userMessage)
            }
            assertEquals(0, fetches)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rejectsHttpUrl() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, _, _ -> fetches += 1 }
        val dir = Files.createTempDirectory("model-root").toFile()
        try {
            try {
                installer.install(
                    pack(url = "http://example.com/model.int8.onnx"),
                    dir,
                    userConfirmed = true,
                )
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.MODEL_DOWNLOAD_DENIED, e.userMessage)
            }
            assertEquals(0, fetches)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun pathEscapeDoesNotFetch() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, _, _ -> fetches += 1 }
        val dir = Files.createTempDirectory("model-root").toFile()
        try {
            try {
                installer.install(
                    ModelPack(
                        id = "asr",
                        relativeDir = "../outside",
                        files = listOf(
                            ModelFileSpec(AsrModelLayout.MODEL_FILE, helloSha, "https://example.com/model.int8.onnx"),
                        ),
                    ),
                    dir,
                    userConfirmed = true,
                )
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.MODEL_DOWNLOAD_DENIED, e.userMessage)
            }
            assertEquals(0, fetches)
            assertFalse(File(dir.parentFile, "outside").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun hashMismatchDeletesPart() = runTest {
        val dir = Files.createTempDirectory("model-root").toFile()
        try {
            val installer = ModelInstaller { _, dest, _ -> dest.writeBytes(hello) }
            try {
                installer.install(pack(sha = "0".repeat(64)), dir, userConfirmed = true)
                throw AssertionError("expected AgentException")
            } catch (e: AgentException) {
                assertEquals(UserFacingErrors.MODEL_HASH_MISMATCH, e.userMessage)
            }
            val packDir = File(dir, AsrModelLayout.DIR)
            assertFalse(File(packDir, AsrModelLayout.MODEL_FILE).exists())
            assertFalse(File(packDir, AsrModelLayout.MODEL_FILE + ".part").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun successMatchesAsrLayout() = runTest {
        val dir = Files.createTempDirectory("model-root").toFile()
        try {
            val installer = ModelInstaller { _, dest, progress ->
                dest.writeBytes(hello)
                progress(hello.size.toLong(), hello.size.toLong())
            }
            installer.install(
                ModelPack(
                    id = "asr",
                    relativeDir = AsrModelLayout.DIR,
                    files = listOf(
                        ModelFileSpec(AsrModelLayout.MODEL_FILE, helloSha, "https://example.com/model.int8.onnx"),
                        ModelFileSpec(AsrModelLayout.TOKENS_FILE, helloSha, "https://example.com/tokens.txt"),
                    ),
                ),
                dir,
                userConfirmed = true,
            )
            assertTrue(AsrModelLayout.isPresent(File(dir, AsrModelLayout.DIR)))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun pack(
        url: String = "https://example.com/model.int8.onnx",
        sha: String = helloSha,
    ) = ModelPack(
        id = "asr",
        relativeDir = AsrModelLayout.DIR,
        files = listOf(ModelFileSpec(AsrModelLayout.MODEL_FILE, sha, url)),
    )
}
