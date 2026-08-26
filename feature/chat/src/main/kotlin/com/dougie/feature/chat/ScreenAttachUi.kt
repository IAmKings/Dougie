package com.dougie.feature.chat

data class ScreenAttachUi(
    val captureId: String,
    val width: Int,
    val height: Int,
)

fun screenAttachChipLabel(width: Int, height: Int): String = "已附上 · ${width}×${height}"
