# Implement: Confirm 卡弹出动效

## Order

1. `chatConfirmCard` / `chatFeedItemsWithoutConfirm` / `nextConfirmEnter` + `ChatUiStateTest`。
2. `ChatFeed` 只吃无 Confirm 的列表。
3. 对话 `Box` 上覆盖压暗 + 底部 `ConfirmToolCard`；250ms FastOutSlowIn 上滑；`play` 用 remember 锁首帧。
4. 压暗 clickable 无动作。`ANIMATOR_DURATION_SCALE == 0` 只淡入。
5. spec：component / hook / quality / state-management。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:chat:testDebugUnitTest
```

JDK 17。真机：等确认时列表压暗、卡从下滑出；点压暗不消失；终止仍取消整轮；打开已在等确认的窗口不重播。

## Do not

- 离场动效、点压暗=拒绝、超时拒绝、思考→工具切换、共享元素。
- `task.py start` 未获规划摘要批准前改产品代码。
