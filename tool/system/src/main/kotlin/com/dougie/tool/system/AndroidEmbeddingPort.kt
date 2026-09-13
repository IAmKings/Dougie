package com.dougie.tool.system

import com.dougie.core.memory.EmbeddingPort
import com.dougie.core.memory.poolClsOrVector
import com.dougie.core.tool.BertWordPiece
import com.dougie.core.tool.BertWordPieceSpec
import com.dougie.core.tool.EmbedModelLayout
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class AndroidEmbeddingPort(
    private val modelDir: File,
) : EmbeddingPort {
    override fun isReady(): Boolean =
        EmbedModelLayout.isPresent(modelDir) && EmbedOrtJni.isAvailable()

    override fun prepareQuery(text: String): String = QUERY_PREFIX + text

    override suspend fun embed(text: String): FloatArray? {
        if (!isReady()) return null
        return try {
            val vocab = BertWordPiece.loadVocab(File(modelDir, EmbedModelLayout.VOCAB_FILE))
            val (ids, mask) = BertWordPiece.encode(text, SPEC, vocab)
            val raw = EmbedOrtJni.embedTokens(modelDir, ids, mask) ?: return null
            val pooled = poolClsOrVector(raw)
            if (pooled.isEmpty() || pooled.any { it.isNaN() }) null else pooled
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："
        val SPEC = BertWordPieceSpec(
            maxLen = 64,
            cls = "[CLS]",
            sep = "[SEP]",
            pad = "[PAD]",
            unk = "[UNK]",
        )
    }
}
