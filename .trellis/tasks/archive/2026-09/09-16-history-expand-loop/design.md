# Design: 任务卡展开工具步骤

## Boundary

| 模块 | 职责 |
|------|------|
| `:feature:history` | `HistoryToolStep`；`toHistoryItem` 从 `toolTrace` 映射；`HistoryCard` 展开 UI |
| Chat / Debug / codec / DB | 不改 |

不新建模块。不依赖 `:feature:chat`。不 bump DB。

## Data

```kotlin
data class HistoryToolStep(
    val toolCallId: String,
    val toolName: String,
    val statusLabel: String,
)
```

`HistoryItem.steps: List<HistoryToolStep> = emptyList()`。

`statusLabel`：`SUCCESS` → 成功；`FAILED` → 失败；其余 → 进行中。

不把 `argsSummary` / `resultJson` 拷进 `HistoryItem`。

## UI

- 循环行：左 `循环 n · toolChain`（现网），右仅当 `steps.isNotEmpty()` 时 `TextButton`「展开」/「收起」，`contentDescription` 同文案。
- `expanded`：`remember(item.taskId) { mutableStateOf(false) }`。离开页丢失。
- 展开：循环行下逐行 13sp `OnSurfaceVariant` Monospace：`$toolName  ·  $statusLabel`。
- 点击：卡片主体 `clickable(onOpen)`；按钮自己的 `onClick` 消费手势，不触发 `onOpen`。

## Don't

- 整卡改成手风琴。
- dump 参数 / `resultJson`。
- 为中文名改 Chat 或抽 `toolDisplayName`。
- Compose UI 测试。
