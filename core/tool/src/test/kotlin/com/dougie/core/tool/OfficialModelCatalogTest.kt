package com.dougie.core.tool

import com.dougie.core.tool.SHA256
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
    fun standardCatalogHasAsrTtsAndIntent() {
        val offers = OfficialModelCatalog.standard()
        assertEquals(3, offers.size)
        assertEquals(listOf("asr", "tts", "intent"), offers.map { it.id })
        assertEquals("语音识别", offers[0].title)
        assertEquals("约 230MB", offers[0].sizeLabel)
        assertEquals("语音合成", offers[1].title)
        assertEquals("约 116MB", offers[1].sizeLabel)
        assertEquals("意图理解", offers[2].title)
        assertEquals("约 12MB", offers[2].sizeLabel)
        assertTrue(offers[0].isConfigured())
        assertTrue(offers[1].isConfigured())
        assertTrue(offers[2].isConfigured())
        assertEquals(OfficialModelCatalog.DEFAULT_ASR_MODEL.httpsUrl, offers[0].pack.files[0].httpsUrl)
        assertEquals(OfficialModelCatalog.DEFAULT_ASR_MODEL.sha256, offers[0].pack.files[0].sha256)
        assertEquals(
            listOf(
                IntentModelLayout.MODEL_FILE,
                IntentModelLayout.TOKENIZER_FILE,
                IntentModelLayout.LABELS_FILE,
                IntentModelLayout.VOCAB_FILE,
            ),
            offers[2].pack.files.map { it.name },
        )
        assertEquals(OfficialModelCatalog.DEFAULT_INTENT_MODEL.sha256, offers[2].pack.files[0].sha256)
        assertTrue(offers[2].pack.files[0].httpsUrl.startsWith("https://"))
        assertTrue(offers[2].pack.files.all { it.httpsUrl.startsWith("https://") })
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
    fun intentCatalogIsFourHttpsFilesNotTestdataGemm() {
        val offer = OfficialModelCatalog.standard()[2]
        assertEquals(4, offer.pack.files.size)
        assertTrue(offer.pack.files.all { it.httpsUrl.contains("intent-minirbt-v2") })
        assertTrue(offer.pack.files.all { SHA256.matches(it.sha256) })
        val testdata = javaClass.getResourceAsStream("/intent-pack/model.onnx")!!.use { it.readBytes() }
        val testdataSha = java.security.MessageDigest.getInstance("SHA-256")
            .digest(testdata).joinToString("") { "%02x".format(it) }
        assertTrue(offer.pack.files[0].sha256 != testdataSha)
    }

    @Test
    fun intentInstalledRequiresOnnxTokenizerLabelsAndVocab() {
        val dir = Files.createTempDirectory("model-root").toFile()
        try {
            val offer = OfficialModelCatalog.intent(https, https, https, https)
            val packDir = File(dir, IntentModelLayout.DIR)
            packDir.mkdirs()
            File(packDir, "model.gguf").writeText("x")
            assertFalse(offer.isInstalled(dir))
            File(packDir, IntentModelLayout.MODEL_FILE).writeText("x")
            File(packDir, IntentModelLayout.TOKENIZER_FILE).writeText("{}")
            File(packDir, IntentModelLayout.LABELS_FILE).writeText("query_time")
            assertFalse(offer.isInstalled(dir))
            File(packDir, IntentModelLayout.VOCAB_FILE).writeText("[PAD]\n")
            assertTrue(offer.isInstalled(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
