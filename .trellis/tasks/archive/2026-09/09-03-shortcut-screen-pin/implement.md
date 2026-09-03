# Implement: 短路径截屏钉附件

## Checklist

1. `ScreenFrameStore` / `InMemoryScreenFrameStore`：可选 JPEG；`clearAll` 一并清。`ScreenCaptureTool` 捕获后 `put` + 存 JPEG；JSON 仍只有 id/宽高。
2. `LoopEngine` 短路径：`attachments.size >= 4` → Halt `ATTACHMENTS_FULL`。单测 AC3。
3. `DougieApplication.onTaskFinished`：LOCAL_INTENT + 成功 `screen_capture` → `addScreen`；否则 `releaseAfterTask`。LLM 成功捕获走 else。
4. `MainActivity` 任务结束后 `syncChips`。`ChatAttachmentSession` 测 adopt / 满额。
5. 改 spec：`state-management.md` 短路径可钉芯片；`directory-structure.md` 附件段；`error-handling` 满额也可来自短路径。
6. `:core:tool:test` `:core:runtime:test` `:app:testPlayDebugUnitTest` `:app:checkChannelLeak`。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :core:runtime:test :app:testPlayDebugUnitTest :app:checkChannelLeak
```

真机：授权后 Chat「截个屏」出现芯片；再发一句，模型能指认屏幕且请求无 SCREEN 图。满 4 张再截失败。LLM 让截屏不钉芯片。

## Risky

- `addScreen` 里 `clearPin`/`put` 弄丢刚截的帧。
- `onTaskFinished` 在 `clearAll` 之后才读 store。
- LLM 与短路径共用 `capture()` 被误钉。
