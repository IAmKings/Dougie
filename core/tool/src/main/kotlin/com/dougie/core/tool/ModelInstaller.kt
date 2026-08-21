package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

data class ModelFileSpec(
    val name: String,
    val sha256: String,
    val httpsUrl: String,
)

data class ModelPack(
    val id: String,
    val relativeDir: String,
    val files: List<ModelFileSpec>,
)

fun interface ModelHttpGet {
    suspend fun get(url: String, dest: File, onProgress: (downloaded: Long, total: Long) -> Unit)
}

class ModelInstaller(
    private val httpGet: ModelHttpGet,
) {
    suspend fun install(
        pack: ModelPack,
        destRoot: File,
        userConfirmed: Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        if (!userConfirmed) {
            throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
        }
        val dirCanon = preparePackDir(pack, destRoot, requireHttps = true)
        pack.files.forEach { spec ->
            val (target, part) = packTargets(dirCanon, spec.name)
            part.delete()
            try {
                httpGet.get(spec.httpsUrl, part, onProgress)
                val actual = SHA256.hex(part)
                if (actual != spec.sha256.lowercase()) {
                    part.delete()
                    throw AgentException(UserFacingErrors.MODEL_HASH_MISMATCH)
                }
                commitPart(part, target)
            } catch (e: CancellationException) {
                part.delete()
                throw e
            } catch (e: AgentException) {
                part.delete()
                throw e
            } catch (_: Exception) {
                part.delete()
                throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_FAILED)
            }
        }
    }
}

internal fun isHttpsUrl(url: String): Boolean {
    if (!url.startsWith("https://")) return false
    return try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = false) &&
            uri.userInfo == null &&
            !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

internal fun preparePackDir(pack: ModelPack, destRoot: File, requireHttps: Boolean): File {
    pack.files.forEach { spec ->
        if (requireHttps && !isHttpsUrl(spec.httpsUrl)) {
            throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
        }
        if (!isSafeFileName(spec.name)) {
            throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
        }
        if (!SHA256.matches(spec.sha256)) {
            throw AgentException(UserFacingErrors.MODEL_HASH_MISMATCH)
        }
    }
    if (!isSafeRelativeDir(pack.relativeDir)) {
        throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
    }
    val dir = File(destRoot, pack.relativeDir)
    dir.mkdirs()
    val rootCanon = destRoot.canonicalFile
    val dirCanon = dir.canonicalFile
    if (!dirCanon.path.startsWith(rootCanon.path + File.separator) && dirCanon != rootCanon) {
        throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
    }
    return dirCanon
}

internal fun packTargets(dirCanon: File, name: String): Pair<File, File> {
    val target = File(dirCanon, name)
    val part = File(dirCanon, name + ".part")
    if (!target.canonicalFile.path.startsWith(dirCanon.path + File.separator)) {
        throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
    }
    return target to part
}

internal fun commitPart(part: File, target: File) {
    if (target.exists()) target.delete()
    if (!part.renameTo(target)) {
        part.copyTo(target, overwrite = true)
        part.delete()
    }
}

fun isSafeFileName(name: String): Boolean =
    name.isNotEmpty() &&
        !name.contains('/') &&
        !name.contains('\\') &&
        name != "." &&
        name != ".." &&
        !name.contains("..")

fun isSafeRelativeDir(path: String): Boolean {
    if (path.isEmpty() || path.startsWith("/") || path.startsWith("\\")) return false
    val parts = path.split('/', '\\')
    return parts.isNotEmpty() && parts.all { part ->
        part.isNotEmpty() && part != "." && part != ".." && !part.contains("..")
    }
}

object SHA256 {
    private val HEX = Regex("^[0-9a-fA-F]{64}$")

    fun matches(value: String): Boolean = HEX.matches(value)

    fun hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }
}
