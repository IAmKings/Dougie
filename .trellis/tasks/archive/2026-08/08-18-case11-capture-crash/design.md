# Design — Case11 截屏崩溃

## Boundary

`:tool:system` `ScreenCaptureService` + `AndroidScreenCapturePort`。`:core:tool` `ScreenCaptureTool` JSON 合同不变。

## Teardown

1. `onStartCommand`：同步 `startForeground`（mediaProjection 类型）失败则 `completeExceptionally` + `stopSelf`。
2. 捕获跑在 `dougie-capture-run`。
3. `ImageReader` / `VirtualDisplay` 绑在 `HandlerThread("dougie-screen-capture")`。帧到达后摘掉 listener。
4. `finally`：`handler.post` 释放 display、close reader、`unregisterCallback`、`projection.stop()`，再 `quitSafely`。
5. 服务结束：`Handler(Looper.getMainLooper()).post { stopForeground(REMOVE); stopSelf() }`。
6. 捕获最长边 ≤ 720，降低 1440×3168 RGBA 双缓冲压力。JSON 的宽高为实际帧尺寸。

## Compatibility

Android 14+：FGS 类型 mediaProjection 必须在 `getMediaProjection` 之前。Consent 仍一次性：`projection.stop()` 后 `ScreenCaptureConsentStore.clear()`，下次需重新授权。

## Rollback

还原 `ScreenCaptureService` 工作线程 `stopForeground` 即回到崩前行为。
