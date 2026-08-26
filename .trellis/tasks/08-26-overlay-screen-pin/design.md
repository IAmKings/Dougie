# Design: 侧载悬浮球冻帧进对话

## Flow

```
Overlay tap (not drag)
  -> hide overlay view
  -> pinCurrentScreen(requireForeground = false)
  -> show overlay view
  -> startActivity(chatLaunchIntent(applyPinnedScreen = true))
MainActivity (fresh intent, not rotate)
  -> if extra: pinned() -> ScreenAttachUi chip
     else lastOverlayError -> attachedError
```

## Contracts

- `ChatLaunch.EXTRA_APPLY_PINNED_SCREEN` = `com.dougie.app.extra.APPLY_PINNED_SCREEN` (boolean).
- `requestsChat` includes this extra.
- `DougieApplication.pinCurrentScreen(requireForeground: Boolean = true)`.
- Overlay errors: `UserFacingErrors` on Application `overlayAttachError` (string only).
- Hide: `View.INVISIBLE` or temporarily `removeView` before `capture()`; restore after. Do not `stopSelf`.

## Compatibility

- `ScreenCaptureService` FGS teardown unchanged.
- `ScreenCaptureTool` unchanged foreground check.
- Play source set must not import overlay capture.

## Risks

Background MediaProjection previously associated with ColorOS process death if teardown was off-thread. Keep existing HandlerThread/main `stopForeground` path. If PJZ110 still kills, fail closed with `TOOL_FAILED`, do not retry-loop.
