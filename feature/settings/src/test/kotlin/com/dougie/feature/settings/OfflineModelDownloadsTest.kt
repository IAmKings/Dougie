package com.dougie.feature.settings

import com.dougie.core.tool.AsrModelLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.dougie.core.tool.ModelInstaller
import com.dougie.core.tool.ModelSource
import com.dougie.core.tool.OfficialModelCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val https = ModelSource("https://example.com/file", helloSha)

    @Test
    fun unconfiguredDoesNotCallInstall() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, _, _ -> fetches += 1 }
        val dir = Files.createTempDirectory("model-ui").toFile()
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                offers = OfficialModelCatalog.standard(),
                scope = this,
            )
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
    fun requestWithoutConfirmDoesNotFetch() = runTest {
        var fetches = 0
        val installer = ModelInstaller { _, dest, _ ->
            fetches += 1
            dest.writeBytes(hello)
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                offers = listOf(OfficialModelCatalog.asr(https, https)),
                scope = this,
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
    fun confirmInstallsAsrLayout() = runTest {
        val installer = ModelInstaller { _, dest, progress ->
            dest.writeBytes(hello)
            progress(hello.size.toLong(), hello.size.toLong())
        }
        val dir = Files.createTempDirectory("model-ui").toFile()
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                offers = listOf(OfficialModelCatalog.asr(https, https)),
                scope = this,
            )
            downloads.request("asr")
            downloads.confirm()
            advanceUntilIdle()
            val row = downloads.ui.value.rows.single()
            assertTrue(row.installed)
            assertFalse(row.downloading)
            assertNull(row.error)
            assertTrue(AsrModelLayout.isPresent(File(dir, AsrModelLayout.DIR)))
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
        try {
            val downloads = OfflineModelDownloads(
                installer = installer,
                destRoot = dir,
                offers = listOf(OfficialModelCatalog.asr(https, https)),
                scope = this,
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
}
