package com.dougie.core.model

const val CONVERSATION_TITLE_MAX_CHARS = 20

fun normalizeConversationTitle(raw: String): String? {
    val normalized = raw
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()
        .take(CONVERSATION_TITLE_MAX_CHARS)
        .trim()
    return normalized.ifBlank { null }
}

fun conversationDisplayName(
    conversationId: String,
    customTitle: String?,
    numberedFallback: String,
    isUnlistedNew: Boolean = false,
): String {
    val custom = customTitle?.let(::normalizeConversationTitle)
    if (custom != null) return custom
    if (conversationId == ConversationIds.DEFAULT) return "默认会话"
    if (isUnlistedNew) return "新对话"
    return numberedFallback
}
