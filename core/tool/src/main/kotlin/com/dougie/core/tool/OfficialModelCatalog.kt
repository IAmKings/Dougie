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
        "intent" -> IntentModelLayout.isPresent(dir)
        else -> false
    }
}

object OfficialModelCatalog {
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
    ): OfflineModelOffer = OfflineModelOffer(
        id = "intent",
        title = "意图理解",
        sizeLabel = "约 470MB",
        pack = ModelPack(
            id = "intent",
            relativeDir = IntentModelLayout.DIR,
            files = listOf(
                ModelFileSpec(IntentModelLayout.MODEL_FILE, model.sha256, model.httpsUrl),
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
    ): List<OfflineModelOffer> = listOf(
        asr(asrModel, asrTokens),
        tts(ttsModel, ttsTokens, ttsLexicon),
        intent(intentModel),
    )
}
