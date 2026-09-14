package com.dougie.core.tool

import org.junit.Assert.assertEquals
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
            assertEquals(0, FullEvalSet.labeledCount(root))
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
            assertEquals(0, FullEvalSet.labeledCount(root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun labeledCountReadsManifestJsonlNotWav() {
        val root = Files.createTempDirectory("eval-manifest").toFile()
        try {
            val asr = File(root, "eval/asr")
            assertTrue(asr.mkdirs())
            File(asr, "manifest.jsonl").writeText(
                """
                {"id":"d001","reference":"现在几点"}
                {"id":"d002","reference":"打开日历","hypothesis":"打开日历"}

                """.trimIndent() + "\n",
            )
            assertFalse(FullEvalSet.isPresent(root))
            assertEquals(2, FullEvalSet.labeledCount(root))
            assertEquals(File(root, FullEvalSet.MANIFEST_RELATIVE), FullEvalSet.manifestFile(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
