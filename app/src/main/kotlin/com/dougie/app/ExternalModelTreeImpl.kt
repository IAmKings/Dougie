package com.dougie.app

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ModelTreeNames
import com.dougie.core.tool.isSafeFileName
import com.dougie.core.tool.isSafeRelativeDir
import com.dougie.feature.settings.ExternalModelTree
import com.dougie.feature.settings.ExternalModelTreeStatus
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    override suspend fun copyPackFiles(relativeDir: String): List<File> = treeLock.withLock {
        if (!snapshot().ready) return emptyList()
        if (!isSafeRelativeDir(relativeDir)) {
            throw AgentException(UserFacingErrors.MODEL_DOWNLOAD_DENIED)
        }
        val dir = findDir(relativeDir) ?: return emptyList()
        val cache = File(context.cacheDir, "model-tree-scan/${relativeDir.replace('/', '_')}")
        cache.deleteRecursively()
        cache.mkdirs()
        val copied = mutableListOf<File>()
        try {
            dir.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                if (!child.isFile) return@forEach
                if (name.endsWith(".part") || name.endsWith(".gguf") || name == "quant.id") return@forEach
                if (!isSafeFileName(name)) return@forEach
                val dest = File(cache, name)
                val input = context.contentResolver.openInputStream(child.uri)
                    ?: return@forEach
                input.use { stream ->
                    dest.outputStream().use { output -> stream.copyTo(output) }
                }
                copied += dest
            }
            return copied
        } catch (e: CancellationException) {
            copied.forEach { it.delete() }
            throw e
        } catch (e: AgentException) {
            copied.forEach { it.delete() }
            throw e
        } catch (_: Exception) {
            copied.forEach { it.delete() }
            throw AgentException(UserFacingErrors.MODEL_IMPORT_FAILED)
        }
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

    private fun findAmong(starts: List<DocumentFile>, parts: List<String>): DocumentFile? {
        if (parts.isEmpty()) return starts.firstOrNull()
        val next = starts.flatMap { matchingDirs(it, parts.first()) }
        if (next.isEmpty()) return null
        return findAmong(next, parts.drop(1))
    }

    private fun findDir(relativeDir: String): DocumentFile? {
        val root = treeRoot() ?: return null
        return findAmong(listOf(root), relativeDir.split('/'))
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
