# Design: Confirm 卡弹出动效

## Boundary

只改 `:feature:chat`。不改 TaskManager 确认/拒绝/取消语义。不 bump DB。

## Mapper

```kotlin
fun chatConfirmCard(items: List<ChatItem>): ChatItem.ConfirmCard? =
    items.lastOrNull { it is ChatItem.ConfirmCard } as ChatItem.ConfirmCard?

fun chatFeedItemsWithoutConfirm(items: List<ChatItem>): List<ChatItem> =
    items.filter { it !is ChatItem.ConfirmCard }

data class ConfirmEnter(val play: Boolean, val initialized: Boolean, val lastKey: String?)

fun nextConfirmEnter(
    confirmKey: String?,
    initialized: Boolean,
    lastKey: String?,
): ConfirmEnter
```

`nextConfirmEnter`：

- `initialized == false`：`play = false`，记下 `lastKey = confirmKey`（打开窗口已在等确认：不播）。
- 已初始化且 `confirmKey != null && confirmKey != lastKey`：`play = true`。
- 否则不播；`lastKey` 跟随当前 `confirmKey`（含变 null）。

气泡 `nextChatItemEnter` 不变（Confirm 仍进 seen、不进 playKeys）。Feed 传入去掉 Confirm 的列表，避免双画。

## Overlay

对话 `Box(weight(1f))` 内，Feed / EmptyState 之上：

1. 半透明压暗（吃点击、无 onClick 动作）。
2. 底部对齐的现网 `ConfirmToolCard`（文案/按钮不改）。

`translationY`：从卡自身高度（或覆盖层底部外）滑到 0；`alpha` 0→1。`tween(250, FastOutSlowInEasing)`。

`ANIMATOR_DURATION_SCALE == 0f`：`translationY` 保持 0，只淡入压暗和卡。

`remember { play }` 锁住首帧，避免 SideEffect 更新 lastKey 后取消动画（与 `ChatItemEnterMotion` 相同）。

不要 `ModalBottomSheet`、不要盖输入栏（终止必须仍可见）、不要点压暗调用 `onReject` / `onCancel`。

## Tests

`ChatUiStateTest`：

- 等确认列表：`chatConfirmCard` 非空；`chatFeedItemsWithoutConfirm` 不含 Confirm。
- 首帧已有 confirmKey → play false。
- 已初始化、key 从 null 到新 key → play true。
- 同一 key 再来 → play false。
- 变 null 后再来新 key → play true。

无 Compose UI 测试。

## Don't

- 列表里再画一张 Confirm。
- 复用气泡进入的 8dp / 200ms。
- 离场动效、超时拒绝、共享元素。
