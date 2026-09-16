# Implement: 思考中发送位改为终止

## Order

1. `ChatUiState.canCancel` + `toChatUiState` / `mergeChatUiState`；`ChatUiStateTest`。
2. `ChatViewModel.cancel()` → `taskManager.cancel()`。
3. `ChatInputBar` 右钮：`canCancel` 优先于发送与停止播报。
4. 需要时补 `LoopEngineTest`：AWAITING_CONFIRMATION 时 `cancel()` → CANCELLED。
5. spec：`state-management.md`、`component-guidelines.md`、`quality-guidelines.md`。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:chat:testDebugUnitTest :core:runtime:test
```

JDK 17。真机：发送后右钮变终止；点后失败「任务已取消。」；等确认同样能整轮取消；完成后恢复发送；播报中仍是停止播报。

## Do not

- Confirm 上滑、改 CANCELLED 字、进场动效、DB bump。
- `task.py start` 未获规划摘要批准前改产品代码。
