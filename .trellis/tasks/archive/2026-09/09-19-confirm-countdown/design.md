# Design: 确认卡倒计时

## Boundary

`:core:model` 加可选 `confirmDeadlineAt`。`:core:runtime` LoopEngine 写入、codec 编解码。`:feature:chat` 只读剩余秒。UI 不 `reject()`。不 ALTER SQLite。

## Domain

```kotlin
data class AgentTask(..., val confirmDeadlineAt: Long? = null)

const val CONFIRM_TIMEOUT_MS = 60_000L

fun confirmCountdownCopy(deadlineAt: Long, nowMs: Long): String {
    val n = confirmRemainingSeconds(deadlineAt, nowMs)
    return if (n <= 0) "未操作即将视为拒绝"
    else "未操作将在 $n 秒后视为拒绝"
}
```

`confirmRemainingSeconds`: `ceil((deadline-now)/1000)`，下限 0。

进入 `AWAITING_CONFIRMATION` 时 `confirmDeadlineAt = now + CONFIRM_TIMEOUT_MS`。离开该状态时清 `null`（含确认后执行、拒绝、终止、超时）。`withTimeout(CONFIRM_TIMEOUT_MS)` 仍用同一常量。

## Persist

`TaskSnapshotCodec` 可选 `confirmDeadlineAt`。旧快照缺字段 → null。不是 DB bump。

## Chat

`ConfirmCard` 带 `confirmDeadlineAt`。`ConfirmToolCard` 在按钮上方用 `LaunchedEffect` 每秒（或 `withFrameNanos` 够 1s 精度）重读 `now`，显示 `confirmCountdownCopy`。不要 `remember { 60 }` 本地倒数。

离开再回来：任务上的 deadline 还在，数字接着走。

## Tests

- LoopEngine：进入等待时 deadline ≈ now+60s；超时仍 `CONFIRM_REJECTED`。
- Codec roundtrip。
- `confirmRemainingSeconds` / copy：60s→60，0→即将视为拒绝。
- ChatUiState 把 deadline 映射到 ConfirmCard。

## Don't

- UI 到 0 自己 `reject()`。
- 改 60s。
- Room schema 版本。
