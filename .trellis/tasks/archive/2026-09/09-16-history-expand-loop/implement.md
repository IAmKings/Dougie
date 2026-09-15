# Implement: 任务卡展开工具步骤

## Order

1. `HistoryToolStep` + `toHistoryItem` 映射；`HistoryItemTest`（有迹 / 无迹 / 成功失败进行中）。
2. `HistoryCard`：循环行右侧「展开」/「收起」；展开列表；主体仍 `onOpen`。
3. spec：`state-management.md`、`component-guidelines.md`、`quality-guidelines.md`（History 仍不 dump args）。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:history:testDebugUnitTest
```

JDK 17。真机：有工具的卡可展开/收起；点摘要仍进对话定位；展开区无 JSON。

## Do not

- bump DB、改 Chat、抽 `toolDisplayName`、持久化展开。
- 把 args / resultJson 放进 `HistoryItem`。
- `task.py start` 未获规划摘要批准前改产品代码。
