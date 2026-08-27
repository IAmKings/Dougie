# Implement

1. `ChatLaunch` extra + `chatLaunchIntent(..., applyPinnedScreen)`. JVM test.
2. `pinCurrentScreen(requireForeground: Boolean = true)`.
3. `MainActivity.applyChatIntent`: apply pinned chip / overlay error; skip when `savedInstanceState != null`.
4. Sideload `DougieOverlayService`: coroutine tap → hide → pin(false) → restore → launch. No submit.
5. `:app:testPlayDebugUnitTest` + `:app:checkChannelLeak` + `:core:tool:test`.

## Risky

`ScreenCaptureService.kt` threads; overlay `FLAG_NOT_FOCUSABLE`; do not put gray in extras.
