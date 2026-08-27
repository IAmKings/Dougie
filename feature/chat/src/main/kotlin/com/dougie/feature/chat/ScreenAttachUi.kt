package com.dougie.feature.chat

import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentLimits
import com.dougie.core.model.AttachmentMeta

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

const val ATTACHMENT_MAX = AttachmentLimits.MAX
