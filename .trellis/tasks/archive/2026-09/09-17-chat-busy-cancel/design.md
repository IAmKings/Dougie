# Design: 思考中发送位改为终止

## Boundary

| 模块 | 职责 |
|------|------|
| `:core:runtime` | 已有 `TaskManager.cancel()`；本刀不改语义 |
| `:feature:chat` | `ChatUiState.canCancel`；`ChatViewModel.cancel()`；输入栏右钮 |
| `:app` | `ChatRoute` 把 `onCancel` 接到 ViewModel（若 Route 已转发其它 ViewModel 方法，同样挂上） |

不 bump DB。不改 codec。不改 `UserFacingErrors.CANCELLED`。

## Mapper

```kotlin
val busy = status != COMPLETED && status != FAILED && status != IDLE
canCancel = busy
inputEnabled = !busy
```

`mergeChatUiState` 的 `canCancel` 跟 live 走（与 `inputEnabled` / `canRetry` 一样）。

## Input bar

右钮：

| 条件 | 图标 | contentDescription | 点击 |
|------|------|--------------------|------|
| `canCancel` | Stop | 终止 | `onCancel()`；若 `speakingReply` 也 `onStopReply()` |
| 否则 `speakingReply` | Stop | 停止播报 | `onStopReply()` |
| 否则 | Send | 发送 | 现网 trim 发送 |

`enabled` = `canCancel \|\| speakingReply \|\| (inputEnabled && text.isNotBlank())`。

文本框 / 附件 / 麦仍 `enabled = inputEnabled`。`新对话` 仍在忙碌时禁用。

不要把 `inputEnabled` 改成 true 来「点亮」终止——否则用户能在跑的时候改草稿并误触发送。

## Runtime

`cancel()` 已是 `running?.cancel()` → `CancellationException` → `markCancelled()`。等确认时 loop 仍挂着，同样可取消。空闲再点：`running` 空，no-op。Chat 不在完成态展示终止，所以 UI 不会双点误伤下一轮。

## Tests

- `ChatUiStateTest`：THINKING / AWAITING_CONFIRMATION → `canCancel == true`；COMPLETED / FAILED / IDLE / null → false。
- 现成 `LoopEngineTest.cancelStopsInFlightStreamAndFailsTask` 不够 Confirm 的话，runtime 补一条 cancel-while-awaiting-confirm（若现网 confirm 等待可被 job cancel）。

无 Compose UI 测试。

## Don't

- 把终止做成第二颗按钮。
- Confirm「拒绝」改成 cancel。
- 忙碌时打开文本框。
- 新建失败码。
