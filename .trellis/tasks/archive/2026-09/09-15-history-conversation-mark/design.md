# Design: 任务页按窗口分组并定位语句

## Boundary

| 模块 | 职责 |
|------|------|
| `:feature:history` | `listRecent(50)` → 分节 UI 模型；`stickyHeader`；点击回传 `conversationId` + `taskId` |
| `:feature:chat` | 一次性 `focusListKey`（`{taskId}:user`）；有 focus 时禁止 `shouldFollowChatFeed` 滚到底 |
| `:app` | `onOpenConversation(id, taskId)`：不忙则 `openConversation(id)`、记下 focus、切 Chat |
| `:core:runtime` | `openConversation` 语义不变（最新一条 seed）。不必为 focus 改 store |

不加表、不加列。`:core:*` 可不改；若 `openConversation` 签名要带 focus，也只放在 ChatViewModel / Activity，不进 `AgentTask`。

## History mapping

纯函数（JVM 单测）：

```kotlin
data class HistorySection(
    val conversationId: String,
    val title: String,
    val items: List<HistoryItem>,
)
```

1. `listRecent(50)` 已是新在前。
2. 节顺序 = 各 `conversationId` 在该列表中**首次出现**的顺序（最近活动窗口在上）。
3. 节内 = 该 id 的子序列（仍新在前）。
4. 标题：`ConversationIds.DEFAULT` → `默认会话`。其余按这 50 条从旧到新第一次出现的非 default id 编号为 `对话 2`、`对话 3`…（只有一个非 default 窗口时也叫「对话 2」，避免和「默认会话」抢「对话 1」）。

`HistoryUiState.sections: List<HistorySection>`，可去掉扁平 `items` 或保留派生。

Compose：`LazyColumn` + `stickyHeader` + `items`。不要按节做嵌套可滚动列表。

## Focus scroll

`ChatViewModel`:

- `var pendingFocusKey: String?`（或一次性 `StateFlow`）
- `fun requestFocus(taskId: String)` → `pendingFocusKey = "$taskId:user"`
- ChatRoute：若 `pendingFocusKey` 非空且 `items` 含该 key，`scrollToItem(index)` 后清空；本次不要走「换 firstKey 滚到底」。
- `newConversation()` 清 focus。
- 底栏进 Chat 不调用 `requestFocus`。

Transcript 异步：`openConversation` 返回后 `items` 可能晚一帧。`LaunchedEffect(pendingFocusKey, items.size)` 等到 key 存在再滚。找不到 key（不在 50 条对应的 transcript 里）则退回现有 follow-to-end。

`openConversation` 仍 seed **最新**终态任务，以便该窗口底部的播报/重试；focus 只影响滚动。

## Busy

`MainActivity` 现有 busy 判断保留；busy 时不 `openConversation`、不 `requestFocus`、不切路由。

## Logging

不 log `input` / `conversationId` UUID / 气泡原文。
