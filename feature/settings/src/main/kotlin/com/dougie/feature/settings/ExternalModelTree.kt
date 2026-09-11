package com.dougie.feature.settings

import com.dougie.core.tool.ChatModelLayout
import java.io.File

data class ExternalModelTreeStatus(
    val ready: Boolean,
    val needsReselect: Boolean,
    val label: String,
)

interface ExternalModelTree {
    fun snapshot(): ExternalModelTreeStatus

    suspend fun copyPackFiles(relativeDir: String, names: Set<String>? = null): List<File>

    suspend fun copyInto(
        relativeDir: String,
        destDir: File,
        names: Map<String, String>,
    ): List<File> {
        destDir.mkdirs()
        val copies = copyPackFiles(relativeDir, names.keys)
        return copies.map { src ->
            val destName = names.entries.firstOrNull { (sourceName, _) ->
                src.name.equals(sourceName, ignoreCase = true) ||
                    ChatModelLayout.matchesStoredName(src.name, sourceName)
            }?.value ?: src.name
            val dest = File(destDir, destName)
            if (!src.renameTo(dest)) {
                src.copyTo(dest, overwrite = true)
                src.delete()
            }
            dest
        }
    }

    suspend fun listPackFileNames(relativeDir: String): Set<String> = emptySet()

    suspend fun listPackFiles(relativeDir: String): List<ExternalPackFile> =
        listPackFileNames(relativeDir).map { ExternalPackFile(it) }

    suspend fun writeLayoutFiles(relativeDir: String, files: List<Pair<String, File>>)
}

data class ExternalPackFile(val name: String, val length: Long = -1L)

object NoExternalModelTree : ExternalModelTree {
    override fun snapshot() = ExternalModelTreeStatus(
        ready = false,
        needsReselect = false,
        label = "未选择",
    )

    override suspend fun copyPackFiles(relativeDir: String, names: Set<String>?): List<File> = emptyList()

    override suspend fun writeLayoutFiles(
        relativeDir: String,
        files: List<Pair<String, File>>,
    ) = Unit
}
