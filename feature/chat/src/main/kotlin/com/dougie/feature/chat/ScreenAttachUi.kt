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

fun voiceOverlayStatus(
    holding: Boolean,
    transcribing: Boolean,
    partial: String = "",
): String = when {
    holding && partial.isNotBlank() -> partial.trim()
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

fun attachmentOffersPermissionCenter(error: String?): Boolean =
    error == UserFacingErrors.PERMISSION_DENIED

const val GO_PERMISSION_CENTER = "去权限中心"

fun showAgentReplySpeak(
    isLastItem: Boolean,
    canSpeakReply: Boolean,
    ttsReady: Boolean,
    text: String,
): Boolean =
    isLastItem && canSpeakReply && ttsReady && text.isNotBlank()

fun agentReplySpeakLabel(speakingReply: Boolean): String =
    if (speakingReply) "停止播报" else "播报"

data class VoiceInsert(
    val text: String,
    val cursor: Int,
)

fun insertVoiceTranscript(
    draft: String,
    start: Int,
    end: Int,
    spoken: String,
): VoiceInsert {
    val from = start.coerceIn(0, draft.length)
    val to = end.coerceIn(from, draft.length)
    val piece = spoken.trim()
    if (piece.isEmpty()) return VoiceInsert(draft, to)
    val left = draft.substring(0, from)
    val right = draft.substring(to)
    val prefix = if (left.isNotEmpty() && !left.last().isWhitespace()) " " else ""
    val suffix = if (right.isNotEmpty() && !right.first().isWhitespace()) " " else ""
    return VoiceInsert(
        text = left + prefix + piece + suffix + right,
        cursor = left.length + prefix.length + piece.length,
    )
}

fun appendVoiceTranscript(draft: String, spoken: String): String =
    insertVoiceTranscript(draft, draft.length, draft.length, spoken).text

const val ATTACHMENT_MAX = AttachmentLimits.MAX
