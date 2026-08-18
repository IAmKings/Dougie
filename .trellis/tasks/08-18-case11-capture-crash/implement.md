# Implement — Case11 截屏崩溃

1. 收口 `ScreenCaptureService.kt`（主线程停 FGS、HandlerThread 释放、MAX_CAPTURE_WIDTH）。
2. 确认 `AndroidScreenCapturePort` 仍 `startForegroundService`；不要在端口里 `stopForeground`。
3. 尝试 `assemblePlayDebug` 并 `adb install -r`（若 KSP 仍解析失败，记 findings 并等网络/缓存恢复）。
4. PJZ110：权限中心授权投屏 → 立刻回对话保持前台 → 截屏+匹配 → 观察 10s 不崩 → 再测拒绝授权。
5. 更新父任务 `08-18-device-e2e-signoff/findings.md` Case 11 行（本任务不归档签字父任务）。

验证：真机现象 + 如可编译则 `:tool:system` 既有单测。无法在 JVM 上复现 MediaProjection 崩溃。
