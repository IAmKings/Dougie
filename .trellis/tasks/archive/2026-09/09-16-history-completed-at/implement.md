# Implement: 任务卡完成时刻

## Order

1. `formatCompletedAt` + `HistoryItemTest`（今天/昨天/同年/跨年、null、14:32:59）。
2. `toHistoryItem` 写入 `completedAtLabel`；`HistoryCard` 元信息行纳入第三段。
3. spec：`state-management.md`、`type-safety.md`、`quality-guidelines.md`、`directory-structure.md`。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:history:testDebugUnitTest
```

JDK 17。真机：新完成的卡有「今天 HH:mm」；旧卡无时刻。

## Do not

- bump DB、相对时间、Chat/Debug、改展开。
- `task.py start` 未获规划摘要批准前改产品代码。
