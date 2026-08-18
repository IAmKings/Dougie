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
        IntentModelLayout.Q4_ID, IntentModelLayout.Q8_ID ->
            IntentModelLayout.installedQuantId(dir) == id
        else -> false
    }
}

object OfficialModelCatalog {
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
    val DEFAULT_INTENT_Q4 = ModelSource(
        httpsUrl = "https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf",
        sha256 = "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a",
    )
    val DEFAULT_INTENT_Q8 = ModelSource(
        httpsUrl = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q8_0.gguf",
        sha256 = "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031",
    )
    val DEFAULT_INTENT_MODEL = DEFAULT_INTENT_Q8

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

    fun intentQ4(model: ModelSource = ModelSource()): OfflineModelOffer = intentOffer(
        id = IntentModelLayout.Q4_ID,
        title = "意图理解 Q4",
        sizeLabel = "约 378MB，更快更省存储，精度略低",
        model = model,
    )

    fun intentQ8(model: ModelSource = ModelSource()): OfflineModelOffer = intentOffer(
        id = IntentModelLayout.Q8_ID,
        title = "意图理解 Q8",
        sizeLabel = "约 639MB，精度更高，更吃内存与算力",
        model = model,
    )

    fun standard(
        asrModel: ModelSource = ModelSource(),
        asrTokens: ModelSource = ModelSource(),
        ttsModel: ModelSource = ModelSource(),
        ttsTokens: ModelSource = ModelSource(),
        ttsLexicon: ModelSource = ModelSource(),
        intentQ4Source: ModelSource = ModelSource(),
        intentQ8Source: ModelSource = ModelSource(),
    ): List<OfflineModelOffer> = listOf(
        asr(asrModel.ifBlank(DEFAULT_ASR_MODEL), asrTokens.ifBlank(DEFAULT_ASR_TOKENS)),
        tts(
            ttsModel.ifBlank(DEFAULT_TTS_MODEL),
            ttsTokens.ifBlank(DEFAULT_TTS_TOKENS),
            ttsLexicon.ifBlank(DEFAULT_TTS_LEXICON),
        ),
        intentQ4(intentQ4Source.ifBlank(DEFAULT_INTENT_Q4)),
        intentQ8(intentQ8Source.ifBlank(DEFAULT_INTENT_Q8)),
    )

    private fun intentOffer(
        id: String,
        title: String,
        sizeLabel: String,
        model: ModelSource,
    ): OfflineModelOffer = OfflineModelOffer(
        id = id,
        title = title,
        sizeLabel = sizeLabel,
        pack = ModelPack(
            id = id,
            relativeDir = IntentModelLayout.DIR,
            files = listOf(
                ModelFileSpec(IntentModelLayout.MODEL_FILE, model.sha256, model.httpsUrl),
            ),
        ),
    )
}

private fun ModelSource.ifBlank(fallback: ModelSource): ModelSource =
    if (httpsUrl.isBlank()) fallback else this
