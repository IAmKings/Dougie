# Implement

## Checklist

1. `ScreenFrameStore`：pin / `pinned()` / `clearPin()`；`InMemoryScreenFrameStore` 单测（pin 期间 `put` 不覆盖或 Tool 短路）。
2. `ScreenCaptureTool`：有 pin 则返回 pinned 元数据、不调 `port.capture()`。
3. `AgentTask` + `TaskSnapshotCodec` + `TaskStoreTest`：可选绑定字段，旧快照仍可解码。
4. `TaskManager.submit`：增加绑定参数（默认 null）；结束时 `clearPin`。空 input 仍拒绝。
5. `OpenAICompatibleProvider.systemPrompt`：绑定一行；`OpenAICompatibleProviderTest` 断言 body 含 id/宽高、无 base64。
6. `:app`：附上调用现有 port + store.put + pin；错误回调中文。`MainActivity` 把 `onAttachScreen` / chip 状态传入 `ChatRoute`（chip 可 hoist，比照 `chatDraftState`）。
7. `ChatScreen`：麦克风旁「附上屏幕」；芯片与 ×；发送成功清 chip。`ChatUiStateTest` 只测映射（若有 chip 映射函数）。
8. `:app:checkChannelLeak`。

## Validation

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :core:runtime:test :core:llm:test :core:model:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak
```

真机：前台附上 → 芯片宽高 → 发送含 screen_match 的话 → JSON 无像素；拒绝投屏不出现已附上。

## Risky files

- `ScreenCaptureTool.kt` / `AndroidScreenCapturePort.kt` / `ScreenCaptureService.kt`：不要改 FGS 线程与 `stop()` 主线程约定。
- `TaskSnapshotCodec.kt`：缺字段必须可解码。
- `ChatScreen.kt`：不要在 composable 里调 port。

## Rollback

去掉 pin 与芯片后，`screen_capture` 恢复每次真截。
