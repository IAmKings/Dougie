package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File

/**
 * Eval-only Kokoro pack. Lives under [DIR], never [TtsModelLayout.DIR].
 * Not part of [OfficialModelCatalog.standard].
 */
object KokoroEvalLayout {
    const val ID = "kokoro-eval"
    const val DIR = "eval/tts/kokoro"
    const val ARCHIVE_FILE = "kokoro-int8-multi-lang-v1_1.tar.bz2"
    const val MODEL_FILE = "model.int8.onnx"
    const val VOICES_FILE = "voices.bin"
    const val TOKENS_FILE = "tokens.txt"
    const val LEXICON_ZH_FILE = "lexicon-zh.txt"
    const val LEXICON_EN_FILE = "lexicon-us-en.txt"
    const val DATA_DIR = "espeak-ng-data"
    const val DICT_DIR = "dict"
    const val ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2"
    /** SHA-256 of the 147031220-byte GitHub `tts-models` asset (not the inner ONNX). */
    const val ARCHIVE_SHA256 = "a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6"

    fun pack(): ModelPack = ModelPack(
        id = ID,
        relativeDir = DIR,
        files = listOf(ModelFileSpec(ARCHIVE_FILE, ARCHIVE_SHA256, ARCHIVE_URL)),
    )

    fun isPresent(modelDir: File): Boolean = layoutFilesPresent(resolvedDir(modelDir))

    fun resolvedDir(modelDir: File): File {
        if (layoutFilesPresent(modelDir)) return modelDir
        val kids = modelDir.listFiles().orEmpty().filter { it.isDirectory }
        return kids.firstOrNull { layoutFilesPresent(it) } ?: modelDir
    }

    fun lexicon(modelDir: File): String {
        val root = resolvedDir(modelDir)
        return listOf(LEXICON_EN_FILE, LEXICON_ZH_FILE).joinToString(",") { name ->
            File(root, name).absolutePath
        }
    }

    fun extractArchive(modelDir: File) {
        if (isPresent(modelDir)) return
        val archive = File(modelDir, ARCHIVE_FILE)
        if (!archive.isFile || archive.length() <= 0L) return
        val rootCanon = modelDir.canonicalFile
        archive.inputStream().buffered().use { raw ->
            BZip2CompressorInputStream(raw).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    while (true) {
                        val entry: TarArchiveEntry = tar.nextEntry ?: break
                        val relative = sanitizeTarName(entry.name)
                        if (relative == null) {
                            skipTarEntry(tar, entry)
                            continue
                        }
                        val dest = File(modelDir, relative)
                        val destCanon = dest.canonicalFile
                        if (!destCanon.path.startsWith(rootCanon.path + File.separator) && destCanon != rootCanon) {
                            throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
                        }
                        if (entry.isDirectory) {
                            dest.mkdirs()
                            continue
                        }
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { out -> tar.copyTo(out) }
                    }
                }
            }
        }
        if (isPresent(modelDir)) {
            archive.delete()
        }
    }

    private fun layoutFilesPresent(dir: File): Boolean {
        val files = listOf(MODEL_FILE, VOICES_FILE, TOKENS_FILE, LEXICON_ZH_FILE, LEXICON_EN_FILE)
        val okFiles = files.all { name ->
            val file = File(dir, name)
            file.isFile && file.length() > 0L
        }
        return okFiles &&
            File(dir, DATA_DIR).isDirectory &&
            File(dir, DICT_DIR).isDirectory
    }

    private fun sanitizeTarName(raw: String): String? {
        val name = raw.replace('\\', '/').trim().trimStart('/')
        if (name.isEmpty() || name == ".") return null
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun skipTarEntry(tar: TarArchiveInputStream, entry: TarArchiveEntry) {
        if (entry.isDirectory || entry.size <= 0L) return
        var left = entry.size
        val buf = ByteArray(8192)
        while (left > 0) {
            val n = tar.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) break
            left -= n
        }
    }
}
