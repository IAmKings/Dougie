package com.dougie.core.tool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FullEvalSetTest {
    @Test
    fun absentDirectoryIsNotPresent() {
        val root = Files.createTempDirectory("eval-missing").toFile()
        try {
            assertFalse(FullEvalSet.isPresent(root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun wavFileMarksSetPresent() {
        val root = Files.createTempDirectory("eval-present").toFile()
        try {
            val asr = File(root, "eval/asr")
            assertTrue(asr.mkdirs())
            File(asr, "clip.wav").writeText("not-a-real-wav")
            assertTrue(FullEvalSet.isPresent(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
