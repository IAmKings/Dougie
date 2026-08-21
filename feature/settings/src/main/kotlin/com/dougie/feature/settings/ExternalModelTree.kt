package com.dougie.feature.settings

import java.io.File

data class ExternalModelTreeStatus(
    val ready: Boolean,
    val needsReselect: Boolean,
    val label: String,
)

interface ExternalModelTree {
    fun snapshot(): ExternalModelTreeStatus

    suspend fun copyPackFiles(relativeDir: String): List<File>

    suspend fun writeLayoutFiles(relativeDir: String, files: List<Pair<String, File>>)
}

object NoExternalModelTree : ExternalModelTree {
    override fun snapshot() = ExternalModelTreeStatus(
        ready = false,
        needsReselect = false,
        label = "未选择",
    )

    override suspend fun copyPackFiles(relativeDir: String): List<File> = emptyList()

    override suspend fun writeLayoutFiles(
        relativeDir: String,
        files: List<Pair<String, File>>,
    ) = Unit
}
