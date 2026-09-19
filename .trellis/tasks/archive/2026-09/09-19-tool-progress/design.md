# Design: 工具不确定进度条

## Boundary

只改 `:feature:chat` Compose + 纯函数。不改 `toChatUiState` 列表结构、`listKey`、TaskManager、DB。

## Mapping

抽出 JVM 可测的：

```kotlin
fun ToolTraceStatus.showsToolProgress(): Boolean =
    this == PENDING || this == EXECUTING
```

`ConfirmCard` 不走这条。History `HistoryToolStep` 不引用。

## Compose

`ToolCallCard`：`entry.status.showsToolProgress()` 为真时，在标题 `Row` 和终端 `Text` 之间放 Material3 不确定 `LinearProgressIndicator()`（无 `progress` lambda）。颜色 `DougieColors.StatusExecuting`。通栏。不要用 Settings 那种带 `progress = { }` 的确定进度。

`SUCCESS` / `FAILED` 分支不组合该条，避免完成态还在跑。不要 `animateItem`。不要改 `ToolSwitchEnterMotion` / `StatusSwitchFade`。

打开窗口时若仍 `EXECUTING`：当前态就有条，不是进入动效。`ANIMATOR_DURATION_SCALE == 0` 交给系统把不确定动画停住；不要自己加 `translationY` 或假百分比。

## Don't

- Confirm overlay 进度条。
- 另起「步骤：」行。
- 给 History **展开** 画条。
- 把 Settings 下载确定进度抄过来当 Chat 不确定进度。
