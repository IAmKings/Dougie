package com.dougie.core.tool

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KokoroEvalLayoutTest {
    @Test
    fun evalPackIsHttpsPinnedAndNotProductTtsDir() {
        val pack = KokoroEvalLayout.pack()
        assertEquals(KokoroEvalLayout.ID, pack.id)
        assertEquals(KokoroEvalLayout.DIR, pack.relativeDir)
        assertFalse(pack.relativeDir == TtsModelLayout.DIR)
        assertFalse(pack.relativeDir.startsWith("${TtsModelLayout.DIR}/"))
        assertEquals(1, pack.files.size)
        assertEquals(KokoroEvalLayout.ARCHIVE_FILE, pack.files[0].name)
        assertEquals(KokoroEvalLayout.ARCHIVE_URL, pack.files[0].httpsUrl)
        assertEquals(KokoroEvalLayout.ARCHIVE_SHA256, pack.files[0].sha256)
        assertEquals(
            "a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6",
            pack.files[0].sha256,
        )
        assertTrue(pack.files[0].httpsUrl.startsWith("https://"))
        assertTrue(SHA256.matches(pack.files[0].sha256))
        assertTrue(isSafeFileName(pack.files[0].name))
        assertTrue(isSafeRelativeDir(pack.relativeDir))
    }

    @Test
    fun extractArchiveLaysOutModelAndSkipsZipSlip() {
        val dir = Files.createTempDirectory("kokoro-eval-layout").toFile()
        try {
            writeFixtureArchive(File(dir, KokoroEvalLayout.ARCHIVE_FILE), slip = true)
            assertFalse(KokoroEvalLayout.isPresent(dir))
            KokoroEvalLayout.extractArchive(dir)
            assertTrue(KokoroEvalLayout.isPresent(dir))
            val root = KokoroEvalLayout.resolvedDir(dir)
            assertTrue(File(root, KokoroEvalLayout.MODEL_FILE).isFile)
            assertTrue(File(root, KokoroEvalLayout.DATA_DIR).isDirectory)
            assertTrue(File(root, KokoroEvalLayout.DICT_DIR).isDirectory)
            assertFalse(File(dir, KokoroEvalLayout.ARCHIVE_FILE).exists())
            assertFalse(File(dir.parentFile, "evil.txt").exists())
            assertTrue(KokoroEvalLayout.lexicon(dir).contains(KokoroEvalLayout.LEXICON_ZH_FILE))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun extractLeavesProductTtsDirUntouched() {
        val root = Files.createTempDirectory("kokoro-eval-vits").toFile()
        try {
            val vits = File(root, TtsModelLayout.DIR)
            vits.mkdirs()
            val sentinel = File(vits, TtsModelLayout.MODEL_FILE)
            sentinel.writeText("vits-keep")
            val evalDir = File(root, KokoroEvalLayout.DIR)
            writeFixtureArchive(File(evalDir, KokoroEvalLayout.ARCHIVE_FILE), slip = false)
            KokoroEvalLayout.extractArchive(evalDir)
            assertTrue(KokoroEvalLayout.isPresent(evalDir))
            assertEquals("vits-keep", sentinel.readText())
            assertFalse(File(vits, KokoroEvalLayout.MODEL_FILE).exists())
            assertEquals(TtsModelLayout.DIR, "models/tts")
            assertFalse(KokoroEvalLayout.DIR == TtsModelLayout.DIR)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeFixtureArchive(archive: File, slip: Boolean) {
        archive.parentFile.mkdirs()
        archive.outputStream().use { raw ->
            BZip2CompressorOutputStream(raw).use { bz ->
                TarArchiveOutputStream(bz).use { tar ->
                    tar.putRaw("bundle/${KokoroEvalLayout.MODEL_FILE}", "model")
                    tar.putRaw("bundle/${KokoroEvalLayout.VOICES_FILE}", "voices")
                    tar.putRaw("bundle/${KokoroEvalLayout.TOKENS_FILE}", "tokens")
                    tar.putRaw("bundle/${KokoroEvalLayout.LEXICON_ZH_FILE}", "zh")
                    tar.putRaw("bundle/${KokoroEvalLayout.LEXICON_EN_FILE}", "en")
                    tar.putRaw("bundle/${KokoroEvalLayout.DATA_DIR}/phontab", "phone")
                    tar.putRaw("bundle/${KokoroEvalLayout.DICT_DIR}/jieba.dict.utf8", "dict")
                    if (slip) {
                        tar.putRaw("../evil.txt", "nope")
                    }
                }
            }
        }
    }

    private fun TarArchiveOutputStream.putRaw(name: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val entry = TarArchiveEntry(name)
        entry.size = bytes.size.toLong()
        putArchiveEntry(entry)
        write(bytes)
        closeArchiveEntry()
    }
}
