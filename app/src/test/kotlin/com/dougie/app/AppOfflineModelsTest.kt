package com.dougie.app

import com.dougie.core.tool.ChatModelLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppOfflineModelsTest {
    @Test
    fun chatDownloadRowMatchesChannel() {
        val ids = AppOfflineModels.offers.map { it.id }
        if (BuildConfig.IS_SIDELOAD) {
            assertEquals(
                listOf(
                    "asr",
                    "tts",
                    "intent",
                    ChatModelLayout.ID,
                    ChatModelLayout.MINICPM1B_ID,
                    ChatModelLayout.MINICPM2B_ID,
                ),
                ids,
            )
        } else {
            assertEquals(listOf("asr", "tts", "intent"), ids)
            assertFalse(ids.contains(ChatModelLayout.ID))
            assertFalse(ids.contains(ChatModelLayout.MINICPM1B_ID))
            assertFalse(ids.contains(ChatModelLayout.MINICPM2B_ID))
        }
    }

    @Test
    fun localChatReadyFollowsLayoutOnlyOnSideload() {
        val dir = Files.createTempDirectory("chat-layout-channel").toFile()
        try {
            val pack = File(dir, ChatModelLayout.DIR)
            pack.mkdirs()
            File(pack, ChatModelLayout.MODEL_FILE).writeText("x")
            if (BuildConfig.IS_SIDELOAD) {
                assertTrue(ChannelHooks.localChatReady(dir))
                File(pack, ChatModelLayout.MINICPM1B_FILE).writeText("y")
                assertFalse(ChannelHooks.localChatReady(dir))
                assertTrue(ChannelHooks.localChatReady(dir, ChatModelLayout.MINICPM1B_ID))
            } else {
                assertFalse(ChannelHooks.localChatReady(dir))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
