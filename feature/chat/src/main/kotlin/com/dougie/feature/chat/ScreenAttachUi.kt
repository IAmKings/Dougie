package com.dougie.feature.chat

import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentLimits
import com.dougie.core.model.AttachmentMeta
import com.dougie.core.model.UserFacingErrors

data class ChatAttachmentUi(
    val id: String,
    val kind: AttachmentKind,
    val width: Int,
    val height: Int,
)

fun ChatAttachmentUi.chipLabel(): String =
    "${kindLabel(kind)} · ${width}×${height}"

fun kindLabel(kind: AttachmentKind): String = when (kind) {
    AttachmentKind.SCREEN -> "屏幕"
    AttachmentKind.GALLERY -> "相册"
    AttachmentKind.CAMERA -> "拍照"
}

fun AttachmentMeta.toUi(): ChatAttachmentUi =
    ChatAttachmentUi(id = id, kind = kind, width = width, height = height)

fun voiceOverlayStatus(holding: Boolean, transcribing: Boolean): String = when {
    holding -> "正在录音"
    transcribing -> "正在进行本地识别..."
    else -> ""
}

const val SPEAKING_REPLY_STATUS = "正在播报..."

fun attachmentStatusLine(speakingReply: Boolean, error: String?): String? = when {
    speakingReply -> SPEAKING_REPLY_STATUS
    !error.isNullOrBlank() -> error
    else -> null
}

fun attachmentStatusIsError(speakingReply: Boolean, error: String?): Boolean =
    !speakingReply && !error.isNullOrBlank()

fun attachmentOffersDownload(error: String?): Boolean =
    error == UserFacingErrors.SPEECH_MODEL_MISSING ||
        error == UserFacingErrors.TTS_REPLY_UNAVAILABLE

fun showAgentReplySpeak(
    isLastItem: Boolean,
    canSpeakReply: Boolean,
    ttsReady: Boolean,
    text: String,
): Boolean =
    isLastItem && canSpeakReply && ttsReady && text.isNotBlank()

fun agentReplySpeakLabel(speakingReply: Boolean): String =
    if (speakingReply) "停止播报" else "播报"

fun appendVoiceTranscript(draft: String, spoken: String): String {
    val piece = spoken.trim()
    if (piece.isEmpty()) return draft
    val base = draft.trimEnd()
    return if (base.isEmpty()) piece else "$base $piece"
}

const val ATTACHMENT_MAX = AttachmentLimits.MAX
