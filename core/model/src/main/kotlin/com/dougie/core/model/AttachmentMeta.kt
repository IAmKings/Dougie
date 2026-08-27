package com.dougie.core.model

object AttachmentLimits {
    const val MAX = 4
}

enum class AttachmentKind {
    SCREEN,
    GALLERY,
    CAMERA,
}

data class AttachmentMeta(
    val id: String,
    val kind: AttachmentKind,
    val width: Int,
    val height: Int,
)
