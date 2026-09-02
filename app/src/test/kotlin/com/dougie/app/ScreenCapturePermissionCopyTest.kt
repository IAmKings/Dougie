package com.dougie.app

import com.dougie.feature.permissions.ScreenCapturePermissionCopy
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenCapturePermissionCopyTest {
    @Test
    fun actionLabelEndsSessionWhenGranted() {
        assertEquals("去授权屏幕截取", ScreenCapturePermissionCopy.actionLabel(false))
        assertEquals("结束截屏授权", ScreenCapturePermissionCopy.actionLabel(true))
        assertEquals(true, ScreenCapturePermissionCopy.SUBTITLE.contains("多次截屏"))
        assertEquals(true, ScreenCapturePermissionCopy.SUBTITLE.contains("不会发给模型"))
    }
}
