package com.dougie.core.runtime

import com.dougie.core.model.ConversationIds

interface ConversationPointer {
    fun currentId(): String
    fun setCurrentId(id: String)
}

class InMemoryConversationPointer(
    initial: String = ConversationIds.DEFAULT,
) : ConversationPointer {
    @Volatile
    private var id: String = initial.ifBlank { ConversationIds.DEFAULT }

    override fun currentId(): String = id

    override fun setCurrentId(id: String) {
        this.id = id.ifBlank { ConversationIds.DEFAULT }
    }
}
