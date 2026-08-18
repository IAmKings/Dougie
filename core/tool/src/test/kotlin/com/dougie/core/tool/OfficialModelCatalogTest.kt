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
        val offer = OfficialModelCatalog.asr()
        assertFalse(offer.isConfigured())
    }

    @Test
    fun standardCatalogUsesOfficialHttpsDefaults() {
        val offers = OfficialModelCatalog.standard()
        assertEquals(4, offers.size)
        assertEquals(listOf("asr", "tts", "intent-q4", "intent-q8"), offers.map { it.id })
        assertEquals("语音识别", offers[0].title)
        assertEquals("约 230MB", offers[0].sizeLabel)
        assertEquals("语音合成", offers[1].title)
        assertEquals("约 116MB", offers[1].sizeLabel)
        assertEquals("意图理解 Q4", offers[2].title)
        assertTrue(offers[2].sizeLabel.startsWith("约 378MB"))
        assertEquals("意图理解 Q8", offers[3].title)
        assertTrue(offers[3].sizeLabel.startsWith("约 639MB"))
        offers.forEach { offer ->
            assertTrue(offer.isConfigured())
            offer.pack.files.forEach { spec ->
                assertTrue(spec.httpsUrl.startsWith("https://huggingface.co/"))
                assertTrue(SHA256.matches(spec.sha256))
            }
        }
        assertEquals(OfficialModelCatalog.DEFAULT_ASR_MODEL.httpsUrl, offers[0].pack.files[0].httpsUrl)
        assertEquals(OfficialModelCatalog.DEFAULT_ASR_MODEL.sha256, offers[0].pack.files[0].sha256)
        assertEquals(OfficialModelCatalog.DEFAULT_INTENT_Q4.httpsUrl, offers[2].pack.files[0].httpsUrl)
        assertTrue(offers[2].pack.files[0].httpsUrl.contains("Qwen3-0.6B-Q4_K_M.gguf"))
        assertEquals(OfficialModelCatalog.DEFAULT_INTENT_Q8.httpsUrl, offers[3].pack.files[0].httpsUrl)
        assertTrue(offers[3].pack.files[0].httpsUrl.contains("Qwen3-0.6B-Q8_0.gguf"))
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

    @Test
    fun intentQuantMarkerSelectsOneOffer() {
        val dir = Files.createTempDirectory("model-root").toFile()
        try {
            val q4 = OfficialModelCatalog.intentQ4(https)
            val q8 = OfficialModelCatalog.intentQ8(https)
            val packDir = File(dir, IntentModelLayout.DIR)
            packDir.mkdirs()
            File(packDir, IntentModelLayout.MODEL_FILE).writeText("x")
            assertTrue(q8.isInstalled(dir))
            assertFalse(q4.isInstalled(dir))
            IntentModelLayout.writeQuantId(packDir, IntentModelLayout.Q4_ID)
            assertTrue(q4.isInstalled(dir))
            assertFalse(q8.isInstalled(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
