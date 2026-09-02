# Design: 投屏会话

## Session

`:tool:system` 持有进程内会话：`ConsentStore` token + 可选已创建的 `MediaProjection`。

1. 权限中心 `save(resultCode, data)`。此时 `hasToken()==true`，尚无 FGS。
2. 第一次 `AndroidScreenCapturePort.capture()`：`startForegroundService` → 服务内 `startForeground` → `getMediaProjection` **一次** → 记下 projection 与捕获用 `HandlerThread`。
3. 每次抓帧：复用同一 ImageReader + VirtualDisplay，等下一帧 JPEG+gray。**不** release display（release 会 `onStop`），**不** `projection.stop()`，**不** `stopSelf`。
4. 第二次 `capture()`：FGS 已在则 `startService`（后台禁止再 `startForegroundService`），复用同一 projection 与 display。

## End

- 权限中心结束：`stopService` / ACTION_STOP → HandlerThread 上 `projection.stop()` → 主线程 `stopForeground`/`stopSelf` → `ConsentStore.clear()`。
- `MediaProjection.Callback.onStop`：拆 display/FGS，**不** `clear()`。下次 `getMediaProjection` 失败再 `clear()`。
- 进程死：自然清空。

`hasProjectionConsent()` 仍为 `hasToken()`。结束必须 clear，避免「看起来已授权、getMediaProjection 必失败」。

## FGS / ColorOS

- 会话期间通知 channel 可沿用 `dougie_screen_capture`，文案改为「Dougie 可以截取屏幕」（抓帧瞬间可短暂改「正在截取屏幕」，非必须）。
- `stopForeground`/`stopSelf` 只在结束会话时、且在主线程。
- 抓帧线程与释放 VD 仍在创建它们的 HandlerThread（与 case11 相同）。

## UI

`:feature:permissions` `PermissionKind.SCREEN_CAPTURE`：已授权主按钮「结束截屏授权」→ `onEndProjectionSession`（`:app` 调 port/service stop + refresh）。未授权仍 `createScreenCaptureIntent()`。

Play 无 overlay；会话逻辑在 `app/src/main` + `:tool:system`，不进 sideload-only。

## Tests

JVM：`FakeScreenCapturePort` 本就可多次 `capture()`，Loop/Tool 单测保持。新增权限文案纯函数（若抽出）。服务行为真机 AC1/AC2。`:app:checkChannelLeak`。
