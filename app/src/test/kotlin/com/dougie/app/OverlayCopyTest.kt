package com.dougie.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OverlayCopyTest {
    @Test
    fun sideloadOverlayBodyExplainsCaptureAndGrants() {
        val file = File("src/sideload/res/values/strings.xml")
        assertTrue(file.isFile)
        val text = file.readText()
        assertFalse(text.contains("点按打开对话"))
        assertTrue(text.contains("截取屏幕"))
        assertTrue(text.contains("显示在其他应用上层"))
        assertTrue(text.contains("投屏"))
        assertTrue(text.contains("截屏需要投屏授权"))
        assertTrue(
            text.contains(
                "截其他应用请打开设置里的悬浮球，切到目标应用后点「截取屏幕」。需要「显示在其他应用上层」和投屏授权。",
            ),
        )
    }
}
