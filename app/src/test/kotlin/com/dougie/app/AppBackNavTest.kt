package com.dougie.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppBackNavTest {
    @Test
    fun chatPreviewClosesWithoutLeavingChat() {
        assertEquals(
            AppNavState(AppRoute.Chat, previewOpen = false),
            consumeBack(AppNavState(AppRoute.Chat, previewOpen = true)),
        )
    }

    @Test
    fun chatWithoutPreviewDoesNotConsume() {
        assertNull(consumeBack(AppNavState(AppRoute.Chat, previewOpen = false)))
    }

    @Test
    fun nestedSettingsPagesReturnToSettings() {
        assertEquals(
            AppNavState(AppRoute.Settings, previewOpen = false),
            consumeBack(AppNavState(AppRoute.Debug, previewOpen = false)),
        )
        assertEquals(
            AppNavState(AppRoute.Settings, previewOpen = false),
            consumeBack(AppNavState(AppRoute.OpenApps, previewOpen = false)),
        )
    }

    @Test
    fun secondaryPagesReturnToChat() {
        listOf(
            AppRoute.Settings,
            AppRoute.Permissions,
            AppRoute.Memory,
            AppRoute.History,
        ).forEach { route ->
            assertEquals(
                AppNavState(AppRoute.Chat, previewOpen = false),
                consumeBack(AppNavState(route, previewOpen = false)),
            )
        }
    }

    @Test
    fun previewDoesNotBlockNonChatPop() {
        assertEquals(
            AppNavState(AppRoute.Chat, previewOpen = false),
            consumeBack(AppNavState(AppRoute.Settings, previewOpen = true)),
        )
        assertEquals(
            AppNavState(AppRoute.Settings, previewOpen = false),
            consumeBack(AppNavState(AppRoute.Debug, previewOpen = true)),
        )
    }
}
