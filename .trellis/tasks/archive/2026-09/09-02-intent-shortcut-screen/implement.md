# Implement: 截屏意图短路径

## Checklist

1. `IntentRouteAnswers`：`screen_capture` → `screen_capture`；`parseShortcutArgs` 与查询工具一样 `"{}"`；`formatFinalAnswer` 校验 `capture_id`/宽高后返回「已截取屏幕。」。改 `IntentRouteAnswersTest`（原 null 断言）。
2. `LoopEngineTest`：高置信截屏跳过 LLM（`FakeScreenCapturePort` + store）；非前台 / 无同意 Halt 且 `streamCount=0`。附件跳过单测保持。
3. `ChatScreen.toolDisplayName`：`screen_capture` →「截取屏幕」；`ChatUiStateTest`。
4. `.trellis/spec/backend/directory-structure.md` 短路径句补上 `screen_capture`。`.trellis/spec/frontend/state-management.md` 工具卡中文名补一行。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:runtime:test :feature:chat:test
```

真机：无附件说「截个屏」→ 本地意图 +「已截取屏幕。」；后台应失败且不走云端。不要求下一句自动 `screen_match`。

## Risky files

- `LoopEngine.kt`：本切片不应改控制流；只靠 `toolNameFor` / parse / format。
- 不要在 Chat 把 `capture_id` 写进附件会话。
