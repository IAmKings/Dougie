package com.dougie.core.tool

/**
 * SAF [androidx.documentfile.provider.DocumentFile.createDirectory] uniquifies
 * (`models (1)`, `models(2)`) instead of returning the existing folder.
 */
object ModelTreeNames {
    fun matchesDirectory(actual: String?, wanted: String): Boolean {
        if (actual.isNullOrEmpty() || wanted.isEmpty()) return false
        if (actual == wanted) return true
        val escaped = Regex.escape(wanted)
        return Regex("^$escaped(?: \\((\\d+)\\)|\\((\\d+)\\))$").matches(actual)
    }

    fun uniquifyRank(actual: String, wanted: String): Int {
        if (actual == wanted) return 0
        val escaped = Regex.escape(wanted)
        val match = Regex("^$escaped(?: \\((\\d+)\\)|\\((\\d+)\\))$").matchEntire(actual)
            ?: return Int.MAX_VALUE
        return match.groupValues.drop(1).first { it.isNotEmpty() }.toInt()
    }

    fun pickReusableName(existing: List<String>, wanted: String): String? =
        existing
            .filter { matchesDirectory(it, wanted) }
            .minByOrNull { uniquifyRank(it, wanted) }

    /** SAF roots may be the parent of `models/`, `models/` itself, or the pack folder. */
    fun dirSearchPaths(relativeDir: String, includeRoot: Boolean = false): List<List<String>> {
        val parts = relativeDir.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return listOf(emptyList())
        return buildList {
            add(parts)
            if (parts.size > 1) add(listOf(parts.last()))
            if (includeRoot) add(emptyList())
        }
    }
}
