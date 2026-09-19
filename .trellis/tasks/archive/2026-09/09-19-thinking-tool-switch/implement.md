# Implement: 思考→工具卡状态切换

## Order

1. `ChatFeed`：`ToolCard` 改走 150ms LinearOutSlowIn 只淡入（锁首帧 `remember { playEnter }`）；用户/思考/助手仍 200ms 气泡进入。
2. `ThinkingChip`：live→「循环 n」150ms 切。
3. `ToolCallCard`：准备/正在调用 → 已调用/失败 150ms 切标题。
4. `ANIMATOR_DURATION_SCALE == 0` 只淡入。不改 follow / Confirm overlay / `listKey`。
5. spec：component / hook / state-management。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:chat:testDebugUnitTest
```

JDK 17。真机：思考后工具卡淡入无上移；确认后同样；卡上文案 150ms 切；打开旧窗不重播。

## Do not

- 进度条、共享元素、Confirm 离场、合并思考与工具行为。
- `task.py start` 未获规划摘要批准前改产品代码。
