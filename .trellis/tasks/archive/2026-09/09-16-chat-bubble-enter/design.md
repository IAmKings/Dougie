# Design: Chat 气泡进入动效

## Boundary

只改 `:feature:chat`。不改 TaskManager、codec、History、Debug。

| 位置 | 职责 |
|------|------|
| `ChatUiState.kt` | 纯函数：哪些 `listKey` 这一帧该播进入，以及下一帧的已见集合 |
| `ChatScreen.kt` `ChatFeed` | 记住已见 key；对 play 集合做 200ms 淡入+上移；Confirm 不包这套动画 |
| 系统动画开关 | `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` → 只淡入 |

## Enter set

```kotlin
data class ChatItemEnter(
    val playKeys: Set<String>,
    val seenKeys: Set<String>,
)

fun nextChatItemEnter(
    items: List<ChatItem>,
    seenKeys: Set<String>?,
): ChatItemEnter
```

- `seenKeys == null`：`ChatFeed` 第一次组合。`playKeys` 空；`seenKeys` = 当前全部 `listKey`（含 Confirm）。
- `items` 空：`playKeys` 空；`seenKeys` 空（新对话之后可以再播）。
- 否则：`playKeys` = 当前 key 里不在 `seenKeys`、且种类不是 `ConfirmCard` 的；下一帧 `seenKeys` = 旧集合 ∪ 当前全部 key。

`ChatFeed` 用 `remember { mutableStateOf<Set<String>?>(null) }`，每帧用当前 `items` 更新。不要用 `firstKey` 当重置条件（空窗发送会把新气泡当成「整窗种子」而不播）。

卸掉 `ChatRoute`（去任务/设置）会丢掉 remember，回来再走 `seenKeys == null`，已有条目不播。这是底栏返回 / History 点卡的路径。

## Motion

新 key 且在 `playKeys`：`alpha` 0→1，`translationY` +8dp→0，`tween(200, FastOutSlowInEasing)`。起点在下方 8dp，再上移到位。

`ANIMATOR_DURATION_SCALE == 0f`：`translationY` 保持 0，仍可短淡入（PRD 降级为淡入）。读 `LocalContext`，不要进 mapper。

不要用会把整列首次出现都当插入的裸 `LazyItemScope.animateItem()`。

同一 `{taskId}:agent` 流式→终态：key 已在 `seenKeys`，不重播。耗时行是同一气泡内容更新，不是新 key。

## Scroll / focus

不改 `shouldFollowChatFeed`、`pendingFocusKey`、`scrollToItem` / `animateScrollToItem`。进入位移不得改 `listKey`。动效期间不禁用点击。

## Tests

`ChatUiStateTest`：

- 首帧满列表 → play 空，seen 含全部 key
- 空列表 → seen 空
- 已见用户句后再来 thinking → 只 play thinking key
- 新 Confirm key → 不在 play
- 已见 `{id}:agent` 再来终态同一 key → 不 play

无 Compose UI 测试。

## Don't

- 给 Confirm 套这套淡入上移
- 用 `firstKey` 变化重置 seen（会吃掉空窗第一次发送）
- 进入动画里改 follow/focus
- 新建 `:core:ui`
- bump DB
