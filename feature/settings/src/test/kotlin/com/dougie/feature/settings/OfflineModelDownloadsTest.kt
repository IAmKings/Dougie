package com.dougie.feature.settings

import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.AsrModelLayout
import com.dougie.core.tool.ChatModelLayout
import com.dougie.core.tool.IntentModelLayout
import com.dougie.core.tool.ModelInstaller
import com.dougie.core.tool.ModelSource
import com.dougie.core.tool.OfficialModelCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModelDownloadsTest {
    private val hello = "hello".toByteArray()
    private val helloSha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    private val world = "world".toByteArray()
    private val worldSha = "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7"
    private val https = ModelSource("https://example.com/file", helloSha)

    @Test
    fun unconfiguredDoesNotCallInstall() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, _, _ -> fetches += 1 }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(installer, dir, tree, listOf(OfficialModelCatalog.asr()))
            downloads.request("asr")
            downloads.confirm()
            advanceUntilIdle()
            assertEquals(0, fetches)
            assertNull(downloads.ui.value.pendingConfirmId)
            assertFalse(downloads.ui.value.rows[0].configured)
            assertEquals(OfflineModelDownloads.UNCONFIGURED, downloads.ui.value.rows[0].error)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun requestWithoutTreeDoesNotConfirm() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, dest, _ ->
            fetches += 1
            dest.writeBytes(hello)
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = false)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            downloads.request("asr")
            advanceUntilIdle()
            assertEquals(0, fetches)
            assertNull(downloads.ui.value.pendingConfirmId)
            assertEquals(OfflineModelDownloads.TREE_MISSING, downloads.ui.value.rows[0].error)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun requestWithLostPermissionAsksReselect() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = false, needsReselect = true)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            downloads.request("asr")
            assertEquals(OfflineModelDownloads.TREE_RESELECT, downloads.ui.value.rows[0].error)
            assertNull(downloads.ui.value.pendingConfirmId)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun requestWithoutConfirmDoesNotFetch() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, dest, _ ->
            fetches += 1
            dest.writeBytes(hello)
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            downloads.request("asr")
            advanceUntilIdle()
            assertEquals(0, fetches)
            assertEquals("asr", downloads.ui.value.pendingConfirmId)
            downloads.dismissConfirm()
            advanceUntilIdle()
            assertEquals(0, fetches)
            assertNull(downloads.ui.value.pendingConfirmId)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun confirmInstallsAsrLayoutAndWritesTree() = runTest {
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            downloads.request("asr")
            downloads.confirm()
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertTrue(row.installed)
            assertFalse(row.downloading)
            assertNull(row.error)
            assertTrue(AsrModelLayout.isPresent(File(dir, AsrModelLayout.DIR)))
            assertTrue(tree.stored[AsrModelLayout.DIR]?.containsKey(AsrModelLayout.MODEL_FILE) == true)
            assertTrue(tree.stored[AsrModelLayout.DIR]?.containsKey(AsrModelLayout.TOKENS_FILE) == true)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cancelLeavesMissingLayoutAndNoPart() = runTest {
        val started = CompletableDeferred<Unit>()
        val installer = ModelInstaller { _, dest, _ ->
            dest.writeBytes(hello)
            started.complete(Unit)
            awaitCancellation()
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            downloads.request("asr")
            downloads.confirm()
            started.await()
            downloads.cancel("asr")
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertFalse(row.installed)
            assertFalse(row.downloading)
            val packDir = File(dir, AsrModelLayout.DIR)
            assertFalse(File(packDir, AsrModelLayout.MODEL_FILE).exists())
            assertFalse(File(packDir, AsrModelLayout.MODEL_FILE + ".part").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanHistoricalGgufDoesNotInstall() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        tree.stored[IntentModelLayout.DIR] = mutableMapOf("model.gguf" to hello)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(
                    OfficialModelCatalog.intent(
                        OfficialModelCatalog.DEFAULT_INTENT_MODEL,
                        OfficialModelCatalog.DEFAULT_INTENT_TOKENIZER,
                        OfficialModelCatalog.DEFAULT_INTENT_LABELS,
                        OfficialModelCatalog.DEFAULT_INTENT_VOCAB,
                    ),
                ),
            )
            downloads.scan()
            advanceUntilIdle()
            assertFalse(downloads.ui.value.rows.single().installed)
            assertFalse(IntentModelLayout.isPresent(File(dir, IntentModelLayout.DIR)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanIntentOnnxPackInstallsWithoutHttp() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, _, _ -> fetches += 1 }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        tree.stored[IntentModelLayout.DIR] = mutableMapOf(
            IntentModelLayout.MODEL_FILE to hello,
            IntentModelLayout.TOKENIZER_FILE to hello,
            IntentModelLayout.LABELS_FILE to hello,
            IntentModelLayout.VOCAB_FILE to hello,
        )
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.intent(https, https, https, https)),
            )
            downloads.scan()
            advanceUntilIdle()
            assertEquals(0, fetches)
            assertTrue(downloads.ui.value.rows.single().installed)
            assertTrue(IntentModelLayout.isPresent(File(dir, IntentModelLayout.DIR)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanMatchingHashesInstallsWithoutHttp() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, _, _ -> fetches += 1 }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        tree.stored[AsrModelLayout.DIR] = mutableMapOf(
            "hf-model.onnx" to hello,
            "hf-tokens.txt" to world,
        )
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(
                    OfficialModelCatalog.asr(
                        ModelSource(httpsUrl = "", sha256 = helloSha),
                        ModelSource(httpsUrl = "", sha256 = worldSha),
                    ),
                ),
            )
            downloads.scan()
            advanceUntilIdle()
            assertEquals(0, fetches)
            val row = downloads.ui.value.rows.single()
            assertTrue(row.installed)
            assertNull(row.error)
            assertTrue(AsrModelLayout.isPresent(File(dir, AsrModelLayout.DIR)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanKeepsFilesDirCacheWhenTreeEmpty() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val asr = File(dir, AsrModelLayout.DIR).apply { mkdirs() }
        File(asr, AsrModelLayout.MODEL_FILE).writeBytes(hello)
        File(asr, AsrModelLayout.TOKENS_FILE).writeBytes(world)
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            assertTrue(downloads.ui.value.rows.single().installed)
            downloads.scan()
            advanceUntilIdle()
            assertTrue(downloads.ui.value.rows.single().installed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanCopyFailureDoesNotMarkInstalled() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = object : ExternalModelTree {
            override fun snapshot() = ExternalModelTreeStatus(
                ready = true,
                needsReselect = false,
                label = "测试目录",
            )

            override suspend fun copyPackFiles(relativeDir: String, names: Set<String>?): List<File> {
                throw com.dougie.core.model.AgentException(UserFacingErrors.MODEL_IMPORT_FAILED)
            }

            override suspend fun writeLayoutFiles(
                relativeDir: String,
                files: List<Pair<String, File>>,
            ) = Unit
        }
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            downloads.scan()
            advanceUntilIdle()
            assertFalse(downloads.ui.value.rows.single().installed)
            assertFalse(downloads.ui.value.scanning)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanCopyFailureKeepsDestCache() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val asr = File(dir, AsrModelLayout.DIR).apply { mkdirs() }
        File(asr, AsrModelLayout.MODEL_FILE).writeBytes(hello)
        File(asr, AsrModelLayout.TOKENS_FILE).writeBytes(world)
        val tree = object : ExternalModelTree {
            override fun snapshot() = ExternalModelTreeStatus(
                ready = true,
                needsReselect = false,
                label = "测试目录",
            )

            override suspend fun copyPackFiles(relativeDir: String, names: Set<String>?): List<File> {
                throw com.dougie.core.model.AgentException(UserFacingErrors.MODEL_IMPORT_FAILED)
            }

            override suspend fun writeLayoutFiles(
                relativeDir: String,
                files: List<Pair<String, File>>,
            ) = Unit
        }
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            downloads.scan()
            advanceUntilIdle()
            assertTrue(downloads.ui.value.rows.single().installed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanChatSiblingsInstallBothWithoutReplace() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        tree.stored[ChatModelLayout.DIR] = mutableMapOf(
            ChatModelLayout.MODEL_FILE to hello,
            ChatModelLayout.MINICPM1B_FILE to world,
        )
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(
                    OfficialModelCatalog.chat(ModelSource(httpsUrl = "", sha256 = helloSha)),
                    OfficialModelCatalog.chat(
                        ModelSource(httpsUrl = "", sha256 = worldSha),
                        id = ChatModelLayout.MINICPM1B_ID,
                        fileName = ChatModelLayout.MINICPM1B_FILE,
                    ),
                ),
            )
            downloads.scan()
            advanceUntilIdle()
            val rows = downloads.ui.value.rows
            assertTrue(rows[0].installed)
            assertTrue(rows[1].installed)
            assertFalse(rows[0].willReplace)
            assertFalse(rows[1].willReplace)
            assertTrue(ChatModelLayout.isPresent(File(dir, ChatModelLayout.DIR), ChatModelLayout.ID))
            assertTrue(
                ChatModelLayout.isPresent(File(dir, ChatModelLayout.DIR), ChatModelLayout.MINICPM1B_ID),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanKeepsChatFilesAlreadyInDestRoot() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val packDir = File(dir, ChatModelLayout.DIR).apply { mkdirs() }
        File(packDir, ChatModelLayout.MODEL_FILE).writeBytes(hello)
        File(packDir, ChatModelLayout.MINICPM1B_FILE).writeBytes(world)
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(
                    OfficialModelCatalog.chat(ModelSource(httpsUrl = "", sha256 = helloSha)),
                    OfficialModelCatalog.chat(
                        ModelSource(httpsUrl = "", sha256 = worldSha),
                        id = ChatModelLayout.MINICPM1B_ID,
                        fileName = ChatModelLayout.MINICPM1B_FILE,
                    ),
                ),
            )
            downloads.scan()
            advanceUntilIdle()
            val rows = downloads.ui.value.rows
            assertTrue(rows[0].installed)
            assertTrue(rows[1].installed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanChatTreeNamesCountAsInstalled() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        tree.stored[ChatModelLayout.DIR] = mutableMapOf(
            ChatModelLayout.MINICPM1B_FILE to world,
            ChatModelLayout.MINICPM2B_FILE to hello,
        )
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(
                    OfficialModelCatalog.chat(https),
                    OfficialModelCatalog.chat(
                        ModelSource(httpsUrl = "", sha256 = worldSha),
                        id = ChatModelLayout.MINICPM1B_ID,
                        fileName = ChatModelLayout.MINICPM1B_FILE,
                    ),
                    OfficialModelCatalog.chat(
                        ModelSource(httpsUrl = "", sha256 = helloSha),
                        id = ChatModelLayout.MINICPM2B_ID,
                        fileName = ChatModelLayout.MINICPM2B_FILE,
                    ),
                ),
            )
            downloads.scan()
            advanceUntilIdle()
            val rows = downloads.ui.value.rows
            assertFalse(rows[0].installed)
            assertTrue(rows[1].installed)
            assertTrue(rows[2].installed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scanWrongHashDoesNotInstall() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        tree.stored[AsrModelLayout.DIR] = mutableMapOf("bad.bin" to "nope".toByteArray())
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(
                    OfficialModelCatalog.asr(
                        ModelSource(httpsUrl = "", sha256 = helloSha),
                        ModelSource(httpsUrl = "", sha256 = helloSha),
                    ),
                ),
            )
            downloads.scan()
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertFalse(row.installed)
            assertNull(row.error)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun requestDuringScanStillOpensConfirmAndDownload() = runTest {
        val gate = CompletableDeferred<Unit>()
        var fetches = 0
        val installer = ModelInstaller { _, dest, progress ->
            fetches += 1
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true, copyGate = gate)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(OfficialModelCatalog.asr(https, https)),
            )
            downloads.scan()
            downloads.request("asr")
            assertEquals("asr", downloads.ui.value.pendingConfirmId)
            downloads.confirm()
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(fetches > 0)
            assertTrue(downloads.ui.value.rows[0].installed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fakeProbeWritesChineseResult() = runTest {
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            var probed = 0
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                cacheRoot = File(dir, "cache"),
                offers = listOf(OfficialModelCatalog.asr(https, https)),
                scope = this,
                probe = OfflineModelProbe {
                    probed += 1
                    ProbeResult(ok = true, message = "语音识别测试通过。")
                },
                tree = tree,
            )
            downloads.request("asr")
            downloads.confirm()
            advanceUntilIdle()
            downloads.probe("asr")
            advanceUntilIdle()
            assertEquals(1, probed)
            val row = downloads.ui.value.rows.single()
            assertEquals(true, row.probeOk)
            assertEquals("语音识别测试通过。", row.probeMessage)
            downloads.probe("asr")
            advanceUntilIdle()
            assertEquals(1, probed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun probeIgnoresSecondClickWhileInFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            var probed = 0
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                cacheRoot = File(dir, "cache"),
                offers = listOf(OfficialModelCatalog.asr(https, https)),
                scope = this,
                probe = OfflineModelProbe {
                    probed += 1
                    gate.await()
                    ProbeResult(ok = true, message = "语音识别测试通过。")
                },
                tree = tree,
            )
            downloads.request("asr")
            downloads.confirm()
            advanceUntilIdle()
            downloads.probe("asr")
            runCurrent()
            downloads.probe("asr")
            assertEquals(1, probed)
            assertTrue(downloads.ui.value.rows.single().probing)
            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(downloads.ui.value.rows.single().probing)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun probeCancelClearsProbing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                cacheRoot = File(dir, "cache"),
                offers = listOf(OfficialModelCatalog.asr(https, https)),
                scope = this,
                probe = OfflineModelProbe {
                    gate.await()
                    ProbeResult(ok = true, message = "语音识别测试通过。")
                },
                tree = tree,
            )
            downloads.request("asr")
            downloads.confirm()
            advanceUntilIdle()
            downloads.probe("asr")
            runCurrent()
            assertTrue(downloads.ui.value.rows.single().probing)
            downloads.cancel("asr")
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertFalse(row.probing)
            assertNull(row.probeOk)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun probeTimeoutShowsError() = runTest {
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                cacheRoot = File(dir, "cache"),
                offers = listOf(OfficialModelCatalog.asr(https, https)),
                scope = this,
                probe = OfflineModelProbe {
                    kotlinx.coroutines.delay(200_000)
                    ProbeResult(ok = true, message = "语音识别测试通过。")
                },
                tree = tree,
            )
            downloads.request("asr")
            downloads.confirm()
            advanceUntilIdle()
            downloads.probe("asr")
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertFalse(row.probing)
            assertEquals(false, row.probeOk)
            assertEquals(UserFacingErrors.MODEL_PROBE_TIMEOUT, row.probeMessage)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun chatProbeWaitsLongerThanAsrTimeout() = runTest {
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                cacheRoot = File(dir, "cache"),
                offers = listOf(OfficialModelCatalog.chat(https)),
                scope = this,
                probe = OfflineModelProbe {
                    kotlinx.coroutines.delay(200_000)
                    ProbeResult(ok = true, message = UserFacingErrors.MODEL_PROBE_CHAT_OK)
                },
                tree = tree,
            )
            downloads.request(ChatModelLayout.ID)
            downloads.confirm()
            advanceUntilIdle()
            downloads.probe(ChatModelLayout.ID)
            testScheduler.advanceTimeBy(90_000)
            runCurrent()
            assertTrue(downloads.ui.value.rows.single().probing)
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertFalse(row.probing)
            assertEquals(false, row.probeOk)
            assertEquals(UserFacingErrors.MODEL_PROBE_TIMEOUT, row.probeMessage)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun staleChatFilenameNeedsUpdateAndStillInstalled() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui-stale-name").toFile()
        val packDir = File(dir, ChatModelLayout.DIR).apply { mkdirs() }
        File(packDir, "Qwen3-0.6B_previous.litertlm").writeBytes(hello)
        File(packDir, ChatModelLayout.MINICPM1B_FILE).writeBytes(world)
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(
                    OfficialModelCatalog.chat(https),
                    OfficialModelCatalog.chat(
                        ModelSource("https://example.com/file", worldSha),
                        id = ChatModelLayout.MINICPM1B_ID,
                        fileName = ChatModelLayout.MINICPM1B_FILE,
                    ),
                ),
            )
            advanceUntilIdle()
            val rows = downloads.ui.value.rows.associateBy { it.id }
            assertTrue(rows.getValue(ChatModelLayout.ID).installed)
            assertTrue(rows.getValue(ChatModelLayout.ID).needsUpdate)
            assertTrue(rows.getValue(ChatModelLayout.MINICPM1B_ID).installed)
            assertFalse(rows.getValue(ChatModelLayout.MINICPM1B_ID).needsUpdate)
            downloads.request(ChatModelLayout.ID)
            assertEquals(ChatModelLayout.ID, downloads.ui.value.pendingConfirmId)
            downloads.request(ChatModelLayout.MINICPM1B_ID)
            assertEquals(ChatModelLayout.ID, downloads.ui.value.pendingConfirmId)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun officialShaMismatchNeedsUpdate() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui-stale-sha").toFile()
        val packDir = File(dir, ChatModelLayout.DIR).apply { mkdirs() }
        File(packDir, ChatModelLayout.MODEL_FILE).writeBytes(world)
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(installer, dir, tree, listOf(OfficialModelCatalog.chat(https)))
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertTrue(row.installed)
            assertTrue(row.needsUpdate)
            downloads.request(ChatModelLayout.ID)
            assertEquals(ChatModelLayout.ID, downloads.ui.value.pendingConfirmId)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun currentChatCannotRedownload() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui-current").toFile()
        val packDir = File(dir, ChatModelLayout.DIR).apply { mkdirs() }
        File(packDir, ChatModelLayout.MODEL_FILE).writeBytes(hello)
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(installer, dir, tree, listOf(OfficialModelCatalog.chat(https)))
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertTrue(row.installed)
            assertFalse(row.needsUpdate)
            downloads.request(ChatModelLayout.ID)
            assertNull(downloads.ui.value.pendingConfirmId)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun chatUpdateDeletesPrivateOldNameAndLeavesOthers() = runTest {
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dest = Files.createTempDirectory("model-ui-update-dest").toFile()
        val extra = Files.createTempDirectory("model-ui-update-extra").toFile()
        val destChat = File(dest, ChatModelLayout.DIR).apply { mkdirs() }
        val extraChat = File(extra, ChatModelLayout.DIR).apply { mkdirs() }
        File(destChat, "Qwen3-0.6B_previous.litertlm").writeBytes(world)
        File(destChat, ChatModelLayout.MINICPM1B_FILE).writeBytes(world)
        File(extraChat, "Qwen3-0.6B_previous.litertlm").writeBytes(world)
        val tree = FakeExternalModelTree(ready = true)
        tree.stored[ChatModelLayout.DIR] = mutableMapOf(
            "Qwen3-0.6B_previous.litertlm" to world,
        )
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dest,
                cacheRoot = File(dest, "cache"),
                offers = listOf(
                    OfficialModelCatalog.chat(https),
                    OfficialModelCatalog.chat(
                        ModelSource("https://example.com/file", worldSha),
                        id = ChatModelLayout.MINICPM1B_ID,
                        fileName = ChatModelLayout.MINICPM1B_FILE,
                    ),
                ),
                scope = this,
                tree = tree,
                extraRoots = listOf(extra),
            )
            advanceUntilIdle()
            assertTrue(downloads.ui.value.rows[0].needsUpdate)
            downloads.request(ChatModelLayout.ID)
            downloads.confirm()
            advanceUntilIdle()
            val row = downloads.ui.value.rows[0]
            assertTrue(row.installed)
            assertFalse(row.needsUpdate)
            assertTrue(File(destChat, ChatModelLayout.MODEL_FILE).isFile)
            assertFalse(File(destChat, "Qwen3-0.6B_previous.litertlm").exists())
            assertFalse(File(extraChat, "Qwen3-0.6B_previous.litertlm").exists())
            assertTrue(File(destChat, ChatModelLayout.MINICPM1B_FILE).isFile)
            assertTrue(
                tree.stored[ChatModelLayout.DIR]?.containsKey("Qwen3-0.6B_previous.litertlm") == true,
            )
            assertTrue(downloads.ui.value.rows[1].installed)
            assertFalse(downloads.ui.value.rows[1].needsUpdate)
        } finally {
            dest.deleteRecursively()
            extra.deleteRecursively()
        }
    }

    @Test
    fun sameOfficialNameShaUpdateOverwritesFile() = runTest {
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dest = Files.createTempDirectory("model-ui-sha-over").toFile()
        val destChat = File(dest, ChatModelLayout.DIR).apply { mkdirs() }
        File(destChat, ChatModelLayout.MODEL_FILE).writeBytes(world)
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = makeDownloads(
                installer,
                dest,
                tree,
                listOf(OfficialModelCatalog.chat(https)),
            )
            advanceUntilIdle()
            assertTrue(downloads.ui.value.rows.single().needsUpdate)
            downloads.request(ChatModelLayout.ID)
            downloads.confirm()
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertTrue(row.installed)
            assertFalse(row.needsUpdate)
            assertEquals(
                hello.toList(),
                File(destChat, ChatModelLayout.MODEL_FILE).readBytes().toList(),
            )
        } finally {
            dest.deleteRecursively()
        }
    }

    @Test
    fun treeCopyIntoOverwritesExistingOfficialName() = runTest {
        val destDir = Files.createTempDirectory("tree-copy-over").toFile()
        val srcDir = Files.createTempDirectory("tree-copy-src").toFile()
        try {
            File(destDir, ChatModelLayout.MODEL_FILE).writeBytes(world)
            val src = File(srcDir, ChatModelLayout.MODEL_FILE).apply { writeBytes(hello) }
            val tree = object : ExternalModelTree {
                override fun snapshot() = ExternalModelTreeStatus(true, false, "t")
                override suspend fun copyPackFiles(
                    relativeDir: String,
                    names: Set<String>?,
                ): List<File> = listOf(src)
                override suspend fun writeLayoutFiles(
                    relativeDir: String,
                    files: List<Pair<String, File>>,
                ) = Unit
            }
            tree.copyInto(
                ChatModelLayout.DIR,
                destDir,
                mapOf(ChatModelLayout.MODEL_FILE to ChatModelLayout.MODEL_FILE),
            )
            assertEquals(
                hello.toList(),
                File(destDir, ChatModelLayout.MODEL_FILE).readBytes().toList(),
            )
        } finally {
            destDir.deleteRecursively()
            srcDir.deleteRecursively()
        }
    }

    @Test
    fun extraRootMarksChatInstalledWithoutDest() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dest = Files.createTempDirectory("model-ui-dest").toFile()
        val extra = Files.createTempDirectory("model-ui-extra").toFile()
        File(extra, ChatModelLayout.DIR).mkdirs()
        File(File(extra, ChatModelLayout.DIR), ChatModelLayout.MINICPM1B_FILE).writeText("x")
        val tree = FakeExternalModelTree(ready = true)
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dest,
                cacheRoot = File(dest, "cache"),
                offers = listOf(
                    OfficialModelCatalog.chat(https),
                    OfficialModelCatalog.chat(
                        https,
                        id = ChatModelLayout.MINICPM1B_ID,
                        fileName = ChatModelLayout.MINICPM1B_FILE,
                    ),
                ),
                scope = this,
                tree = tree,
                extraRoots = listOf(extra),
            )
            assertFalse(downloads.ui.value.rows[0].installed)
            assertTrue(downloads.ui.value.rows[1].installed)
        } finally {
            dest.deleteRecursively()
            extra.deleteRecursively()
        }
    }

    @Test
    fun scanGuessesOddChatNameByLength() = runTest {
        val installer = ModelInstaller { _, _, _ -> error("http") }
        val dir = Files.createTempDirectory("model-ui-chat-len").toFile()
        val tree = FakeExternalModelTree(ready = true)
        tree.stored[ChatModelLayout.DIR] = mutableMapOf(
            "minicpm_wi4b32_wi8_afp32.litertlm" to hello,
        )
        tree.lengths["minicpm_wi4b32_wi8_afp32.litertlm"] = 756L * 1024 * 1024
        try {
            val downloads = makeDownloads(
                installer,
                dir,
                tree,
                listOf(
                    OfficialModelCatalog.chat(https),
                    OfficialModelCatalog.chat(
                        https,
                        id = ChatModelLayout.MINICPM1B_ID,
                        fileName = ChatModelLayout.MINICPM1B_FILE,
                    ),
                    OfficialModelCatalog.chat(
                        https,
                        id = ChatModelLayout.MINICPM2B_ID,
                        fileName = ChatModelLayout.MINICPM2B_FILE,
                    ),
                ),
            )
            downloads.scan()
            advanceUntilIdle()
            val rows = downloads.ui.value.rows.associateBy { it.id }
            assertFalse(rows.getValue(ChatModelLayout.ID).installed)
            assertTrue(rows.getValue(ChatModelLayout.MINICPM1B_ID).installed)
            assertFalse(rows.getValue(ChatModelLayout.MINICPM2B_ID).installed)
            assertTrue(
                File(File(dir, ChatModelLayout.DIR), ChatModelLayout.MINICPM1B_FILE).isFile,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun fixtureBytes(name: String): ByteArray {
        val stream = javaClass.getResourceAsStream("/intent-pack/$name")
            ?: error("missing fixture $name")
        return stream.use { it.readBytes() }
    }

    private fun kotlinx.coroutines.CoroutineScope.makeDownloads(
        installer: ModelInstaller,
        dir: File,
        tree: ExternalModelTree,
        offers: List<com.dougie.core.tool.OfflineModelOffer>,
    ) = OfflineModelDownloads(
        installer = installer,
        destRoot = dir,
        cacheRoot = File(dir, "cache"),
        offers = offers,
        scope = this,
        tree = tree,
        hashDispatcher = coroutineContext[kotlin.coroutines.ContinuationInterceptor] as? kotlinx.coroutines.CoroutineDispatcher
            ?: kotlinx.coroutines.Dispatchers.Default,
    )
}

private class FakeExternalModelTree(
    private var ready: Boolean,
    private val needsReselect: Boolean = false,
    overrideLabel: String = "测试目录",
    private val copyGate: CompletableDeferred<Unit>? = null,
) : ExternalModelTree {
    val stored = mutableMapOf<String, MutableMap<String, ByteArray>>()
    val lengths = mutableMapOf<String, Long>()
    private val label = overrideLabel

    override fun snapshot() = ExternalModelTreeStatus(
        ready = ready && !needsReselect,
        needsReselect = needsReselect,
        label = label,
    )

    override suspend fun copyPackFiles(relativeDir: String, names: Set<String>?): List<File> {
        copyGate?.await()
        val files = stored[relativeDir] ?: return emptyList()
        return files
            .filter { names == null || names.any { want -> ChatModelLayout.matchesStoredName(it.key, want) } }
            .map { (name, bytes) ->
                File.createTempFile("tree-", "-$name").apply { writeBytes(bytes) }
            }
    }

    override suspend fun copyInto(
        relativeDir: String,
        destDir: File,
        names: Map<String, String>,
    ): List<File> {
        copyGate?.await()
        destDir.mkdirs()
        val files = stored[relativeDir] ?: return emptyList()
        return names.mapNotNull { (sourceName, destName) ->
            val bytes = files[sourceName] ?: return@mapNotNull null
            File(destDir, destName).apply { writeBytes(bytes) }
        }
    }

    override suspend fun listPackFileNames(relativeDir: String): Set<String> =
        stored[relativeDir]?.keys.orEmpty()

    override suspend fun listPackFiles(relativeDir: String): List<ExternalPackFile> =
        stored[relativeDir].orEmpty().map { (name, bytes) ->
            ExternalPackFile(name, lengths[name] ?: bytes.size.toLong())
        }

    override suspend fun writeLayoutFiles(relativeDir: String, files: List<Pair<String, File>>) {
        if (!snapshot().ready) {
            throw com.dougie.core.model.AgentException(UserFacingErrors.MODEL_TREE_MISSING)
        }
        val map = stored.getOrPut(relativeDir) { mutableMapOf() }
        files.forEach { (name, file) ->
            map[name] = file.readBytes()
        }
    }
}
