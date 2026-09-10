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
        assertFalse(text.contains("无障碍"))
        assertFalse(text.contains("脚本特权"))
        assertTrue(text.contains("气泡"))
    }

    @Test
    fun playChannelHooksDoNotImportAccessibilityTypes() {
        val file = File("src/play/kotlin/com/dougie/app/ChannelHooks.kt")
        assertTrue(file.isFile)
        val text = file.readText()
        assertFalse(text.contains("AndroidGesturePort"))
        assertFalse(text.contains("DougieAccessibilityService"))
        assertFalse(text.contains("TapSwipeTool"))
        assertFalse(text.contains("JsEvalTool"))
        assertFalse(text.contains("AndroidJsEvalPort"))
        assertFalse(text.contains("quickjs", ignoreCase = true))
        assertFalse(text.contains("PyEvalTool"))
        assertFalse(text.contains("AndroidPyEvalPort"))
        assertFalse(text.contains("chaquopy", ignoreCase = true))
        assertTrue(text.contains("accessibilityPermissionItem"))
        assertTrue(text.contains("scriptPrivileged"))
        assertTrue(text.contains("false"))
    }

    @Test
    fun playChannelToolsDoNotImportJsEval() {
        val file = File("src/play/kotlin/com/dougie/app/ChannelTools.kt")
        assertTrue(file.isFile)
        val text = file.readText()
        assertFalse(text.contains("JsEvalTool"))
        assertFalse(text.contains("AndroidJsEvalPort"))
        assertFalse(text.contains("tool.js"))
        assertFalse(text.contains("PyEvalTool"))
        assertFalse(text.contains("AndroidPyEvalPort"))
        assertFalse(text.contains("chaquopy", ignoreCase = true))
        assertFalse(text.contains("tool.py"))
    }

    @Test
    fun playManifestHasNoSendSmsOrCallPhone() {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue(file.isFile)
        val text = file.readText()
        assertFalse(text.contains("SEND_SMS"))
        assertFalse(text.contains("CALL_PHONE"))
        assertTrue(text.contains("android.intent.action.SENDTO"))
        assertTrue(text.contains("android.intent.action.DIAL"))
    }

    @Test
    fun telecomPortUsesSendToAndDialNotCallOrSmsManager() {
        val file = File("../tool/system/src/main/kotlin/com/dougie/tool/system/AndroidTelecomPort.kt")
        assertTrue(file.isFile)
        val text = file.readText()
        assertTrue(text.contains("ACTION_SENDTO"))
        assertTrue(text.contains("ACTION_DIAL"))
        assertFalse(text.contains("ACTION_CALL"))
        assertFalse(text.contains("SmsManager"))
        assertFalse(text.contains("SEND_SMS"))
        assertFalse(text.contains("CALL_PHONE"))
        assertFalse(text.contains("Log."))
    }
}
