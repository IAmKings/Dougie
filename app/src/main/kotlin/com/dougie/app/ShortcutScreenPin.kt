package com.dougie.app

import com.dougie.core.model.AgentTask
import com.dougie.core.tool.ScreenFrameStore

object ShortcutScreenPin {
    fun adoptIntoComposer(
        task: AgentTask?,
        session: ChatAttachmentSession,
        store: ScreenFrameStore,
    ): Boolean {
        if (!ScreenShortcutHint.shouldShow(task)) return false
        val frame = store.last() ?: return false
        return session.addScreen(frame, store.jpeg(frame.id)).isSuccess
    }
}
