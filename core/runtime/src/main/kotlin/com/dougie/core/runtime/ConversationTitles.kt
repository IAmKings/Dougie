package com.dougie.core.runtime

import com.dougie.core.model.normalizeConversationTitle

interface ConversationTitles {
    fun titles(): Map<String, String>
    fun setTitle(conversationId: String, raw: String)
}

class InMemoryConversationTitles(
    initial: Map<String, String> = emptyMap(),
) : ConversationTitles {
    private val map = LinkedHashMap<String, String>()

    init {
        initial.forEach { (id, raw) -> setTitle(id, raw) }
    }

    override fun titles(): Map<String, String> = map.toMap()

    override fun setTitle(conversationId: String, raw: String) {
        val id = conversationId.ifBlank { return }
        val name = normalizeConversationTitle(raw)
        if (name == null) {
            map.remove(id)
        } else {
            map[id] = name
        }
    }
}
