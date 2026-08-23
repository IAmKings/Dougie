package com.dougie.app

import com.dougie.core.tool.ModelSource
import com.dougie.core.tool.OfficialModelCatalog

object AppOfflineModels {
    val offers = OfficialModelCatalog.standard(
        asrModel = ModelSource(BuildConfig.ASR_MODEL_URL, BuildConfig.ASR_MODEL_SHA256),
        asrTokens = ModelSource(BuildConfig.ASR_TOKENS_URL, BuildConfig.ASR_TOKENS_SHA256),
        ttsModel = ModelSource(BuildConfig.TTS_MODEL_URL, BuildConfig.TTS_MODEL_SHA256),
        ttsTokens = ModelSource(BuildConfig.TTS_TOKENS_URL, BuildConfig.TTS_TOKENS_SHA256),
        ttsLexicon = ModelSource(BuildConfig.TTS_LEXICON_URL, BuildConfig.TTS_LEXICON_SHA256),
        intentModel = ModelSource(BuildConfig.INTENT_MODEL_URL, BuildConfig.INTENT_MODEL_SHA256),
        intentTokenizer = ModelSource(BuildConfig.INTENT_TOKENIZER_URL, BuildConfig.INTENT_TOKENIZER_SHA256),
        intentLabels = ModelSource(BuildConfig.INTENT_LABELS_URL, BuildConfig.INTENT_LABELS_SHA256),
    )
}
