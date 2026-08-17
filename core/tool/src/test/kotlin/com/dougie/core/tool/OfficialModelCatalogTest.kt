package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfficialModelCatalogTest {
    private val helloSha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    private val https = ModelSource("https://example.com/file", helloSha)

    @Test
    fun blankUrlsAreNotConfigured() {
        val offers = OfficialModelCatalog.standard()
        assertEquals(3, offers.size)
        assertEquals(listOf("asr", "tts", "intent"), offers.map { it.id })
        assertEquals("语音识别", offers[0].title)
        assertEquals("约 230MB", offers[0].sizeLabel)
        assertEquals("语音合成", offers[1].title)
        assertEquals("约 116MB", offers[1].sizeLabel)
        assertEquals("意图理解", offers[2].title)
        assertEquals("约 470MB", offers[2].sizeLabel)
        offers.forEach { assertFalse(it.isConfigured()) }
    }

    @Test
    fun asrLayoutFilesMatch() {
        val offer = OfficialModelCatalog.asr(https, https)
        assertTrue(offer.isConfigured())
        assertEquals(AsrModelLayout.DIR, offer.pack.relativeDir)
        assertEquals(
            listOf(AsrModelLayout.MODEL_FILE, AsrModelLayout.TOKENS_FILE),
            offer.pack.files.map { it.name },
        )
    }

    @Test
    fun httpUrlIsNotConfigured() {
        val bad = ModelSource("http://example.com/file", helloSha)
        assertFalse(OfficialModelCatalog.asr(bad, bad).isConfigured())
    }

    @Test
    fun isInstalledUsesAsrLayout() {
        val dir = Files.createTempDirectory("model-root").toFile()
        try {
            val offer = OfficialModelCatalog.asr(https, https)
            assertFalse(offer.isInstalled(dir))
            val packDir = File(dir, AsrModelLayout.DIR)
            packDir.mkdirs()
            File(packDir, AsrModelLayout.MODEL_FILE).writeText("x")
            File(packDir, AsrModelLayout.TOKENS_FILE).writeText("a")
            File(packDir, AsrModelLayout.MODEL_FILE + ".part").writeText("no")
            assertTrue(offer.isInstalled(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
