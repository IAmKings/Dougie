package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BertWordPieceTest {
    @Test
    fun encodeWrapsClsSepAndMasks() {
        val dir = Files.createTempDirectory("bert-wp").toFile()
        try {
            FileTok(dir)
            val spec = BertWordPiece.loadSpec(java.io.File(dir, "tokenizer.json"))
            val vocab = BertWordPiece.loadVocab(java.io.File(dir, "vocab.txt"))
            val (ids, mask) = BertWordPiece.encode("现在几点", spec, vocab)
            assertEquals(8, ids.size)
            assertEquals(vocab.getValue("[CLS]").toLong(), ids[0])
            assertEquals(1L, mask[0])
            assertEquals(vocab.getValue("[SEP]").toLong(), ids[5])
            assertEquals(0L, mask[7])
        } finally {
            dir.deleteRecursively()
        }
    }
}

private fun FileTok(dir: java.io.File) {
    java.io.File(dir, "tokenizer.json").writeText(
        """{"algorithm":"bert_wordpiece","max_len":8}""",
    )
    java.io.File(dir, "vocab.txt").writeText(
        listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "现", "在", "几", "点").joinToString("\n"),
    )
}
