package com.dougie.core.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HashBagEmbeddingPortTest {
    @Test
    fun missingTokenizerIsNotReady() {
        val dir = Files.createTempDirectory("embed-empty").toFile()
        try {
            assertFalse(HashBagEmbeddingPort(dir).isReady())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun parsedTokenizerEmbedsAndSelfCosineIsOne() = runTest {
        val dir = Files.createTempDirectory("embed-hash").toFile()
        try {
            File(dir, HashBagEmbeddingPort.TOKENIZER_FILE).writeText(
                """{"algorithm":"char_ngram_fnv1a32_hash_bag","dim":64,"ngram_min":1,"ngram_max":2}""",
            )
            val port = HashBagEmbeddingPort(dir)
            assertTrue(port.isReady())
            val vec = port.embed("我喜欢喝美式")!!
            assertEquals(64, vec.size)
            assertEquals(1f, cosineSimilarity(vec, vec), 1e-5f)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun unreadableTokenizerIsNotReady() {
        val dir = Files.createTempDirectory("embed-bad").toFile()
        try {
            File(dir, HashBagEmbeddingPort.TOKENIZER_FILE).writeText("{not-hash-bag}")
            assertFalse(HashBagEmbeddingPort(dir).isReady())
        } finally {
            dir.deleteRecursively()
        }
    }
}
