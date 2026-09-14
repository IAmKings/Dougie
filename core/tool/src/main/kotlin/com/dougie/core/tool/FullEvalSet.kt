package com.dougie.core.tool

import java.io.File

/**
 * Optional full ASR dump at repo-root `eval/asr/` (gitignored wav files).
 *
 * Rule D labeled count is [labeledCount] from `eval/asr/manifest.jsonl`, not wav presence.
 * Missing directory or manifest must skip, never fail CI.
 * [isPresent] means any wav exists; that is not a claim that rule D passed.
 */
object FullEvalSet {
    const val MANIFEST_RELATIVE = "eval/asr/manifest.jsonl"

    fun wavDir(repoRoot: File = File(".")): File = File(repoRoot, "eval/asr")

    fun manifestFile(repoRoot: File = File(".")): File = File(repoRoot, MANIFEST_RELATIVE)

    fun isPresent(repoRoot: File = File(".")): Boolean {
        val dir = wavDir(repoRoot)
        return dir.isDirectory && dir.listFiles()?.any { it.isFile && it.extension.equals("wav", ignoreCase = true) } == true
    }

    fun labeledCount(repoRoot: File = File(".")): Int {
        val file = manifestFile(repoRoot)
        if (!file.isFile) return 0
        return AsrEval.loadJsonl(file.readText()).size
    }
}
