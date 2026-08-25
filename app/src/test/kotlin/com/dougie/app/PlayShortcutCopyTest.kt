package com.dougie.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayShortcutCopyTest {
    @Test
    fun playShortcutCopyHasNoSideloadOrOverlayPermissionTraces() {
        val file = File("src/play/res/values/strings.xml")
        assertTrue(file.isFile)
        val text = file.readText()
        assertFalse(text.contains("sideload", ignoreCase = true))
        assertFalse(text.contains("上层显示"))
        assertFalse(text.contains("SYSTEM_ALERT"))
        assertTrue(text.contains("气泡"))
    }
}
