package com.dougie.core.tool

import java.io.File

data class ModelSource(
    val httpsUrl: String = "",
    val sha256: String = "",
)

data class OfflineModelOffer(
    val id: String,
    val title: String,
    val sizeLabel: String,
    val pack: ModelPack,
)

fun OfflineModelOffer.isConfigured(): Boolean =
    pack.files.isNotEmpty() &&
        pack.files.all { spec -> isHttpsUrl(spec.httpsUrl) && SHA256.matches(spec.sha256) }

fun OfflineModelOffer.isInstalled(destRoot: File): Boolean {
    val dir = File(destRoot, pack.relativeDir)
    return when (id) {
        "asr" -> AsrModelLayout.isPresent(dir)
        "tts" -> TtsModelLayout.isPresent(dir)
        IntentModelLayout.ID -> IntentModelLayout.isPresent(dir)
        else -> false
    }
}

object OfficialModelCatalog {
    private const val INTENT_PACK_BASE =
        "https://raw.githubusercontent.com/IAmKings/Dougie/master/core/tool/src/test/resources/intent-pack/"
    val DEFAULT_ASR_MODEL = ModelSource(
        httpsUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-paraformer-zh-2023-09-14/resolve/main/model.int8.onnx",
        sha256 = "f36a0433bcf096bd6d6f11b80a3ac8bed110bdca632fe0d731df8d1a84475945",
    )
    val DEFAULT_ASR_TOKENS = ModelSource(
        httpsUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-paraformer-zh-2023-09-14/resolve/main/tokens.txt",
        sha256 = "59aba8873a2ed1e122c25fee421e25f283b63290efbde85c1f01a853d83cb6e6",
    )
    val DEFAULT_TTS_MODEL = ModelSource(
        httpsUrl = "https://huggingface.co/csukuangfj/vits-zh-hf-fanchen-C/resolve/main/vits-zh-hf-fanchen-C.onnx",
        sha256 = "77c4bf60602d7d83f0e320063d15655e3bfc51d25b728d727203cd13e77521ab",
    )
    val DEFAULT_TTS_TOKENS = ModelSource(
        httpsUrl = "https://huggingface.co/csukuangfj/vits-zh-hf-fanchen-C/resolve/main/tokens.txt",
        sha256 = "34b035b9aeb070df6188b022f29c00e0e142c7ade9f25611ced65db5e9cc8402",
    )
    val DEFAULT_TTS_LEXICON = ModelSource(
        httpsUrl = "https://huggingface.co/csukuangfj/vits-zh-hf-fanchen-C/resolve/main/lexicon.txt",
        sha256 = "9af2824e49e731bf615927c768fdc36bbbe894cac57d8e0088d9c94331b07320",
    )
    val DEFAULT_INTENT_MODEL = ModelSource(
        httpsUrl = "$INTENT_PACK_BASE${IntentModelLayout.MODEL_FILE}",
        sha256 = "90aa53472bfb23b5b43535eb5430719c27cef8b796553e404784ab87b850afee",
    )
    val DEFAULT_INTENT_TOKENIZER = ModelSource(
        httpsUrl = "$INTENT_PACK_BASE${IntentModelLayout.TOKENIZER_FILE}",
        sha256 = "647780993168a2bfc0c9f192b05f082b47bf6b55ff565a7cdade2821fc09536d",
    )
    val DEFAULT_INTENT_LABELS = ModelSource(
        httpsUrl = "$INTENT_PACK_BASE${IntentModelLayout.LABELS_FILE}",
        sha256 = "a559c08ec65060b6298d8fc15cd2a183ed0dc9a3c7328515c2ece8dbf6648245",
    )

    fun asr(
        model: ModelSource = ModelSource(),
        tokens: ModelSource = ModelSource(),
    ): OfflineModelOffer = OfflineModelOffer(
        id = "asr",
        title = "语音识别",
        sizeLabel = "约 230MB",
        pack = ModelPack(
            id = "asr",
            relativeDir = AsrModelLayout.DIR,
            files = listOf(
                ModelFileSpec(AsrModelLayout.MODEL_FILE, model.sha256, model.httpsUrl),
                ModelFileSpec(AsrModelLayout.TOKENS_FILE, tokens.sha256, tokens.httpsUrl),
            ),
        ),
    )

    fun tts(
        model: ModelSource = ModelSource(),
        tokens: ModelSource = ModelSource(),
        lexicon: ModelSource = ModelSource(),
    ): OfflineModelOffer = OfflineModelOffer(
        id = "tts",
        title = "语音合成",
        sizeLabel = "约 116MB",
        pack = ModelPack(
            id = "tts",
            relativeDir = TtsModelLayout.DIR,
            files = listOf(
                ModelFileSpec(TtsModelLayout.MODEL_FILE, model.sha256, model.httpsUrl),
                ModelFileSpec(TtsModelLayout.TOKENS_FILE, tokens.sha256, tokens.httpsUrl),
                ModelFileSpec(TtsModelLayout.LEXICON_FILE, lexicon.sha256, lexicon.httpsUrl),
            ),
        ),
    )

    fun intent(
        model: ModelSource = ModelSource(),
        tokenizer: ModelSource = ModelSource(),
        labels: ModelSource = ModelSource(),
    ): OfflineModelOffer = OfflineModelOffer(
        id = IntentModelLayout.ID,
        title = "意图理解",
        sizeLabel = "约 10–20MB",
        pack = ModelPack(
            id = IntentModelLayout.ID,
            relativeDir = IntentModelLayout.DIR,
            files = listOf(
                ModelFileSpec(IntentModelLayout.MODEL_FILE, model.sha256, model.httpsUrl),
                ModelFileSpec(IntentModelLayout.TOKENIZER_FILE, tokenizer.sha256, tokenizer.httpsUrl),
                ModelFileSpec(IntentModelLayout.LABELS_FILE, labels.sha256, labels.httpsUrl),
            ),
        ),
    )

    fun standard(
        asrModel: ModelSource = ModelSource(),
        asrTokens: ModelSource = ModelSource(),
        ttsModel: ModelSource = ModelSource(),
        ttsTokens: ModelSource = ModelSource(),
        ttsLexicon: ModelSource = ModelSource(),
        intentModel: ModelSource = ModelSource(),
        intentTokenizer: ModelSource = ModelSource(),
        intentLabels: ModelSource = ModelSource(),
    ): List<OfflineModelOffer> = listOf(
        asr(asrModel.ifBlank(DEFAULT_ASR_MODEL), asrTokens.ifBlank(DEFAULT_ASR_TOKENS)),
        tts(
            ttsModel.ifBlank(DEFAULT_TTS_MODEL),
            ttsTokens.ifBlank(DEFAULT_TTS_TOKENS),
            ttsLexicon.ifBlank(DEFAULT_TTS_LEXICON),
        ),
        intent(
            intentModel.ifBlank(DEFAULT_INTENT_MODEL),
            intentTokenizer.ifBlank(DEFAULT_INTENT_TOKENIZER),
            intentLabels.ifBlank(DEFAULT_INTENT_LABELS),
        ),
    )
}

private fun ModelSource.ifBlank(fallback: ModelSource): ModelSource =
    if (httpsUrl.isBlank()) fallback else this
