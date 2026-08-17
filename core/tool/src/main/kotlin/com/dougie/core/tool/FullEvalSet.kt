package com.dougie.core.tool

import java.io.File

/**
 * Optional full ASR dump at repo-root eval/asr/ (gitignored wav files).
 * Missing files must skip, never fail CI.
 */
object FullEvalSet {
    fun wavDir(repoRoot: File = File(".")): File = File(repoRoot, "eval/asr")

    fun isPresent(repoRoot: File = File(".")): Boolean {
        val dir = wavDir(repoRoot)
        return dir.isDirectory && dir.listFiles()?.any { it.isFile && it.extension.equals("wav", ignoreCase = true) } == true
    }
}
