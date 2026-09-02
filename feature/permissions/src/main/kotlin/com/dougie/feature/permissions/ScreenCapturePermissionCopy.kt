package com.dougie.feature.permissions

object ScreenCapturePermissionCopy {
    const val SUBTITLE =
        "系统投屏授权后，同进程内可多次截屏；可在此结束或从通知栏停止。截图只留本机，不会发给模型"

    fun actionLabel(granted: Boolean): String =
        if (granted) "结束截屏授权" else "去授权屏幕截取"
}
