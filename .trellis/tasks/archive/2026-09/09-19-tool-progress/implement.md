# Implement: 工具不确定进度条

## Order

1. `ChatUiState.kt`：`ToolTraceStatus.showsToolProgress()`。`ChatUiStateTest` 锁 PENDING/EXECUTING true、SUCCESS/FAILED false。
2. `ToolCallCard`：标题与结果区之间，条件组合不确定 `LinearProgressIndicator`，色 `StatusExecuting`。
3. 不改 Confirm overlay、150ms 切换、`listKey`、follow。
4. spec：component / quality（Chat 不确定条 vs Settings 确定下载条）。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:chat:testDebugUnitTest
```

JDK 17。真机：时间工具执行中有条、完成后消失；打开微信确认前覆盖层无条、确认后有条；旧窗无条。

## Do not

- 共享元素、Confirm 离场、步骤行、百分比、History 条。
- `task.py start` 未获规划摘要批准前改产品代码。
