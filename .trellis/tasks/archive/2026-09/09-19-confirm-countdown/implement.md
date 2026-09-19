# Implement: 确认卡倒计时

## Order

1. `:core:model`：`confirmDeadlineAt`、`CONFIRM_TIMEOUT_MS`、`confirmRemainingSeconds` / `confirmCountdownCopy` + 单测。
2. LoopEngine 进入等待时写入 deadline；离开清掉。`withTimeout` 用同一常量。
3. `TaskSnapshotCodec` 可选字段 + 测试。
4. `ConfirmCard` 映射 deadline；卡上按钮上方显示倒计时，每秒刷新。
5. spec：state-management / backend directory（可选 snapshot 字段）。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :core:model:test :core:runtime:test :feature:chat:testDebugUnitTest
```

JDK 17。真机：打开微信倒计时从约 60 往下；去任务再回不重置；确认/拒绝/终止立刻生效；超时文案不变。

## Do not

- UI 自己 reject、改 60s、SQLite bump。
- `task.py start` 未获规划摘要批准前改产品代码。
