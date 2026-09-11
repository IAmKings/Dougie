package com.dougie.app

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ChatModelLayout
import com.dougie.core.tool.ModelTreeNames
import com.dougie.core.tool.isSafeFileName
import com.dougie.core.tool.isSafeRelativeDir
import com.dougie.feature.settings.ExternalModelTree
import com.dougie.feature.settings.ExternalModelTreeStatus
import com.dougie.feature.settings.ExternalPackFile
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ExternalModelTreeImpl(
    private val context: Context,
    private val uriProvider: () -> String,
) : ExternalModelTree {
    private val treeLock = Mutex()
    override fun snapshot(): ExternalModelTreeStatus {
        val raw = uriProvider()
        if (raw.isBlank()) {
            return ExternalModelTreeStatus(ready = false, needsReselect = false, label = "未选择")
        }
        val uri = Uri.parse(raw)
        val persisted = context.contentResolver.persistedUriPermissions.any { grant ->
            grant.isReadPermission && grant.isWritePermission && sameTree(grant.uri, uri)
        }
        if (!persisted) {
            return ExternalModelTreeStatus(
                ready = false,
                needsReselect = true,
                label = UserFacingErrors.MODEL_TREE_RESELECT,
            )
        }
        val name = DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() }
        return ExternalModelTreeStatus(ready = true, needsReselect = false, label = name ?: "已选择")
    }

    override suspend fun copyPackFiles(relativeDir: String, names: Set<String>?): List<File> =
        treeLock.withLock {
            copyMatchingLocked(relativeDir, names, destDir = null)
        }

    override suspend fun copyInto(
        relativeDir: String,
        destDir: File,
        names: Map<String, String>,
    ): List<File> = treeLock.withLock {
        destDir.mkdirs()
        copyMatchingLocked(relativeDir, names.keys, destDir, names)
    }

    private suspend fun copyMatchingLocked(
        relativeDir: String,
        names: Set<String>?,
        destDir: File?,
        destNames: Map<String, String> = emptyMap(),
    ): List<File> = withContext(Dispatchers.IO) {
        if (!snapshot().ready) return@withContext emptyList()
        if (!isSafeRelativeDir(relativeDir)) {
            throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
        }
        val dirs = packDirs(relativeDir)
        if (dirs.isEmpty()) return@withContext emptyList()
        val staging = destDir ?: File(context.cacheDir, "model-tree-scan/${relativeDir.replace('/', '_')}")
        if (destDir == null) {
            staging.deleteRecursively()
            staging.mkdirs()
        }
        val copied = mutableListOf<File>()
        val created = mutableListOf<File>()
        val parts = mutableListOf<File>()
        try {
            val depth = if (relativeDir == ChatModelLayout.DIR) 3 else 0
            dirs.forEach { dir ->
                collectFiles(dir, depth).forEach { child ->
                    val name = child.name ?: return@forEach
                    if (names == null && !child.isFile) return@forEach
                    if (names != null && names.none { want ->
                        name.equals(want, ignoreCase = true) ||
                            ChatModelLayout.matchesStoredName(name, want)
                    }) {
                        return@forEach
                    }
                    if (name.endsWith(".part") || name.endsWith(".gguf") || name == "quant.id") {
                        return@forEach
                    }
                    if (!isSafeFileName(name)) return@forEach
                    val destName = destNames.entries.firstOrNull { (sourceName, _) ->
                        name.equals(sourceName, ignoreCase = true) ||
                            ChatModelLayout.matchesStoredName(name, sourceName)
                    }?.value ?: name
                    if (!isSafeFileName(destName)) return@forEach
                    val dest = File(staging, destName)
                    val part = File(staging, "$destName.part")
                    parts += part
                    val input = context.contentResolver.openInputStream(child.uri)
                        ?: return@forEach
                    input.use { stream ->
                        part.outputStream().use { output -> stream.copyTo(output) }
                    }
                    if (dest.exists()) dest.delete()
                    if (!part.renameTo(dest)) {
                        part.copyTo(dest, overwrite = true)
                        part.delete()
                    }
                    created += dest
                    copied += dest
                }
            }
            copied
        } catch (e: CancellationException) {
            created.forEach { it.delete() }
            parts.forEach { it.delete() }
            throw e
        } catch (e: AgentException) {
            created.forEach { it.delete() }
            parts.forEach { it.delete() }
            throw e
        } catch (_: Exception) {
            created.forEach { it.delete() }
            parts.forEach { it.delete() }
            throw AgentException(UserFacingErrors.MODEL_IMPORT_FAILED)
        }
    }

    override suspend fun listPackFileNames(relativeDir: String): Set<String> =
        listPackFiles(relativeDir).map { it.name }.toSet()

    override suspend fun listPackFiles(relativeDir: String): List<ExternalPackFile> =
        treeLock.withLock {
            if (!snapshot().ready) return emptyList()
            if (!isSafeRelativeDir(relativeDir)) return emptyList()
            val acc = linkedMapOf<String, Long>()
            val depth = if (relativeDir == ChatModelLayout.DIR) 3 else 0
            packDirs(relativeDir).forEach { dir ->
                collectFiles(dir, depth).forEach { child ->
                    val name = child.name ?: return@forEach
                    if (name.endsWith(".part") || name.endsWith(".gguf") || name == "quant.id") {
                        return@forEach
                    }
                    if (!isSafeFileName(name)) return@forEach
                    if (relativeDir == ChatModelLayout.DIR &&
                        !name.endsWith(".litertlm", ignoreCase = true)
                    ) {
                        return@forEach
                    }
                    acc[name] = maxOf(acc[name] ?: 0L, child.length())
                }
            }
            acc.map { ExternalPackFile(it.key, it.value) }
        }

    override suspend fun writeLayoutFiles(relativeDir: String, files: List<Pair<String, File>>) =
        treeLock.withLock {
        if (!snapshot().ready) {
            throw AgentException(UserFacingErrors.MODEL_TREE_MISSING)
        }
        if (!isSafeRelativeDir(relativeDir)) {
            throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
        }
        val dir = requireDir(relativeDir)
        files.forEach { (name, source) ->
            if (!isSafeFileName(name)) {
                throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
            }
            val existing = dir.listFiles().firstOrNull { child ->
                child.isFile && child.name == name
            }
            val target = existing
                ?: dir.createFile("application/octet-stream", name)
                ?: throw AgentException(UserFacingErrors.MODEL_TREE_WRITE_FAILED)
            val output = if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.openOutputStream(target.uri, "wt")
            } else {
                context.contentResolver.openOutputStream(target.uri)
            }
                ?: throw AgentException(UserFacingErrors.MODEL_TREE_WRITE_FAILED)
            try {
                output.use { stream ->
                    source.inputStream().use { input -> input.copyTo(stream) }
                }
            } catch (e: CancellationException) {
                if (existing == null) target.delete()
                throw e
            } catch (e: AgentException) {
                if (existing == null) target.delete()
                throw e
            } catch (_: Exception) {
                if (existing == null) target.delete()
                throw AgentException(UserFacingErrors.MODEL_TREE_WRITE_FAILED)
            }
        }
        }

    private fun treeRoot(): DocumentFile? {
        val raw = uriProvider()
        if (raw.isBlank()) return null
        return DocumentFile.fromTreeUri(context, Uri.parse(raw))
    }

    private fun childDirs(parent: DocumentFile): List<DocumentFile> =
        parent.listFiles().filter { child -> child.isDirectory && !child.name.isNullOrEmpty() }

    private fun matchingDirs(parent: DocumentFile, wanted: String): List<DocumentFile> =
        childDirs(parent).filter { ModelTreeNames.matchesDirectory(it.name, wanted) }

    private fun findAll(starts: List<DocumentFile>, parts: List<String>): List<DocumentFile> {
        if (parts.isEmpty()) return starts
        val next = starts.flatMap { matchingDirs(it, parts.first()) }
        if (next.isEmpty()) return emptyList()
        return findAll(next, parts.drop(1))
    }

    private fun findAmong(starts: List<DocumentFile>, parts: List<String>): DocumentFile? =
        findAll(starts, parts).minByOrNull { dir ->
            ModelTreeNames.uniquifyRank(dir.name.orEmpty(), parts.lastOrNull().orEmpty())
        }

    private fun resolveDirs(relativeDir: String): List<DocumentFile> {
        val root = treeRoot() ?: return emptyList()
        val includeRoot = relativeDir == ChatModelLayout.DIR
        val found = mutableListOf<DocumentFile>()
        for (parts in ModelTreeNames.dirSearchPaths(relativeDir, includeRoot)) {
            val dirs = if (parts.isEmpty()) {
                if (rootLooksLikePack(root, relativeDir)) listOf(root) else continue
            } else {
                findAll(listOf(root), parts)
            }
            dirs.forEach { dir ->
                if (found.none { it.uri == dir.uri }) found += dir
            }
        }
        return found
    }

    private fun packDirs(relativeDir: String): List<DocumentFile> {
        val found = resolveDirs(relativeDir).toMutableList()
        if (relativeDir == ChatModelLayout.DIR) {
            treeRoot()?.let { root ->
                if (found.none { it.uri == root.uri }) found += root
            }
        }
        return found
    }

    private fun collectFiles(dir: DocumentFile, depth: Int): List<DocumentFile> {
        val out = mutableListOf<DocumentFile>()
        dir.listFiles().forEach { child ->
            if (child.isDirectory) {
                if (depth > 0) out += collectFiles(child, depth - 1)
            } else {
                out += child
            }
        }
        return out
    }

    private fun rootLooksLikePack(root: DocumentFile, relativeDir: String): Boolean {
        val wanted = when (relativeDir) {
            ChatModelLayout.DIR -> ChatModelLayout.SKU_IDS.mapNotNull { ChatModelLayout.fileName(it) }.toSet()
            else -> emptySet()
        }
        if (wanted.isEmpty()) return false
        return root.listFiles().any { child ->
            val name = child.name ?: return@any false
            if (child.isDirectory) return@any false
            ChatModelLayout.guessSku(name, child.length()) != null ||
                wanted.any { ChatModelLayout.matchesStoredName(name, it) }
        }
    }

    private fun requireDir(relativeDir: String): DocumentFile {
        val parts = relativeDir.split('/')
        var current = treeRoot() ?: throw AgentException(UserFacingErrors.MODEL_TREE_MISSING)
        parts.forEachIndexed { index, part ->
            val rest = parts.drop(index + 1)
            val matches = matchingDirs(current, part)
            val existing = if (rest.isEmpty()) {
                matches.minByOrNull { ModelTreeNames.uniquifyRank(it.name!!, part) }
            } else {
                matches.firstOrNull { findAmong(listOf(it), rest) != null }
                    ?: matches.minByOrNull { ModelTreeNames.uniquifyRank(it.name!!, part) }
            }
            current = existing
                ?: current.createDirectory(part)
                ?: throw AgentException(UserFacingErrors.MODEL_TREE_WRITE_FAILED)
        }
        return current
    }
}

private fun sameTree(persisted: Uri, selected: Uri): Boolean {
    if (persisted == selected) return true
    val a = persisted.toString().trimEnd('/')
    val b = selected.toString().trimEnd('/')
    return a == b || a.startsWith("$b/") || b.startsWith("$a/")
}
