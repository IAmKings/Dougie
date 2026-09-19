# Design: Confirm 卡离场动效

## Boundary

只改 `:feature:chat`。不推迟 `TaskManager.confirm` / `reject` / `cancel`。不改 `listKey`、进场、follow。

## Keep overlay for one exit

`chatConfirmCard` 变 `null` 后仍要画最后一张卡。`ChatScreen` 记住 `exitingCard`：

- 当前有 Confirm：画它，清退出态。
- 当前没有、但刚有过（`initialized` 且 `lastKey != null`）：把上一张卡留下播离场，播完丢掉。
- `initialized == false`（ChatRoute 首帧）：即使没有卡也不播离场。

抽出 JVM：

```kotlin
fun nextConfirmExit(
    confirmKey: String?,
    initialized: Boolean,
    lastKey: String?,
): ConfirmExit  // play, keepLast
```

首帧不 play。之后 `lastKey != null && confirmKey == null` → play。同一 key 还在 → 不 play。

## Motion

与进场对称：250ms FastOutSlowIn。卡 `translationY` 从 0 到自身高度；scrim+卡 alpha 到 0。`ANIMATOR_DURATION_SCALE == 0`：只 alpha，不位移。动效中按钮仍可点，但 TaskManager 已走完，重复点击应保持现网 no-op。

不要 `ModalBottomSheet`。不要等离场结束再 `confirm()`。

## Don't

- 底栏离开再回来播一次离场。
- 改点压暗、超时 60s 逻辑（只让 UI 跟上状态变无）。
- 给工具卡再叠一套等待。
