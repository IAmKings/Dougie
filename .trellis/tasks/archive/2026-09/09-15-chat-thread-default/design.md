# Design: 默认会话 + 新对话 + 窗口多轮

## Boundary

| 模块 | 职责 |
|------|------|
| `:core:model` | `AgentTask.conversationId`，默认 `"default"` |
| `:core:runtime` | codec；`TaskStore.listByConversation`；`TaskManager` 写入当前 id、刷新 transcript、`newConversation` / `openConversation` |
| `:data:preferences` | `current_conversation_id` 独立 key |
| `:data:tasks` | SQLite 实现 `listByConversation`（扫描 `snapshot_json`，不加列） |
| `:feature:chat` | 拼接多轮 `ChatItem`；顶栏「新对话」+ 确认 |
| `:feature:history` | 卡片可点；不分组 |
| `:app` | 注入 pointer；冷启动 seed；History → Chat 带会话 |

不改 `LoopEngine` prompt、`OpenAICompatibleProvider`、`ChatPromptAssembler.localPrompt`。

## conversationId

```kotlin
object ConversationIds {
    const val DEFAULT = "default"
}
```

`AgentTask.conversationId: String = ConversationIds.DEFAULT`。

`TaskSnapshotCodec`：编码必写 `conversationId`；解码缺省/空白 → `DEFAULT`。与现有 `ignoreUnknownKeys` 旧行兼容（`TaskStoreTest.snapshotDecodeWithoutSpeakReplyDefaultsFalse` 同类）。

不升 `dougie_tasks.db` 版本，不加 `conversation_id` 列。v0.1.1 已发出，`onUpgrade` 仍是 drop——本刀不去碰它。

## Current pointer

`ConversationPointer`（JVM 接口，放 `:core:runtime` 或极薄 `:core:model`）：

```kotlin
interface ConversationPointer {
    fun currentId(): String
    fun setCurrentId(id: String)
}
```

- 测试：内存实现，初值 `DEFAULT`。
- 真机：`PreferenceStore` 新 key `current_conversation_id`，`get/set` 立刻 `edit().apply()`，**不要**放进 `ProviderSettings` / `save()`。缺 key → `DEFAULT`。

`TaskManager.submit` 创建任务时 `conversationId = pointer.currentId()`。`retry` 拷贝当前任务的 `conversationId`（同一会话）。

## TaskStore

```kotlin
suspend fun listByConversation(conversationId: String): List<AgentTask>
```

时间升序（`updated_at ASC`）。`InMemoryTaskStore` 按 upsert 顺序过滤即可。

`SqliteTaskStore`：`SELECT snapshot_json FROM agent_tasks ORDER BY updated_at ASC`，decode 后 `conversationId` 相等才收。**禁止** `listRecent(50)` 再过滤冒充当前会话。个人体量全表扫描可接受。不在 SQL 用 `LIKE` 搜 json。

`listRecent(50)` 行为不变（任务页）。

## TaskManager

保留 `task: StateFlow<AgentTask?>`（进行中 / 最近 seed 的那一条）。

新增 `transcript: StateFlow<List<AgentTask>>`：当前会话内、已 `COMPLETED`/`FAILED`、且 `taskId != task.value?.taskId` 的任务，升序。每次 `persist` 成功、`newConversation`、`openConversation`、`seed` 后从 store 刷新。store 为 null 时 transcript 空（cli / 单测无持久化）。

```kotlin
fun newConversation()
fun openConversation(conversationId: String)
```

- 若 `task` 非终态（忙）：两方法 no-op（与 `submit` 拒第二发一致）。
- `newConversation`：若当前会话没有任何气泡（transcript 空且 `task==null`）no-op。否则 `pointer.setCurrentId(UUID)`，`_task = null`，刷新 transcript（应为空）。**不删除**旧 `agent_tasks`。
- `openConversation`：`setCurrentId`；刷新 transcript；将该会话 **updated_at 最新** 的一条 `seed` 到 `_task`（终态），以便重试/播报仍作用在最后一轮。整段气泡来自 transcript + 这条 live。

`recoverInterrupted` 仍全局标失败最新非终态。`DougieApplication`：若失败任务 `conversationId == pointer.currentId()` 才 `seed`；否则只落库，不切窗口。然后无论是否 seed，`TaskManager.refreshTranscript()`（或构造后 load）。

## Chat UI

`ChatViewModel` `combine(task, transcript)` → 一个 `ChatUiState`：

1. transcript 每条 → `toPastChatItems()`：`UserMessage(input)` + 终答或 `任务失败：…`。无 Thinking / Tool / Confirm。
2. 再追加 `task.toChatUiState().items`（现有 5 态）。
3. `inputEnabled` / `canRetry` / `canSpeakReply` / `isEmpty` 仍只由 **live** `task` 决定；live 为 null 且 transcript 空 → `isEmpty=true`。

`ChatUiStateTest` 锁：两轮 past + 一轮 live 的 item 顺序；past 不含 ToolCard。

顶栏 `DougieTopBar` 增加「新对话」图标（`contentDescription` 中文）。空窗口或忙：按钮禁用。有气泡且空闲：点击弹出确认「开始新对话？当前窗口会清空。」确认 → `TaskManager.newConversation()`。不进底栏、不做侧栏。

`:feature:chat` 仍不 import `android.database`。

## History → Chat

`HistoryItem` 增加 `conversationId`。`HistoryCard` `clickable`。`HistoryRoute` 增加 `onOpenConversation: (conversationId: String) -> Unit`。忙时由 Activity 读 `taskManager.task` 状态：非终态则忽略点击（可不改卡片外观，或禁用；至少行为 no-op）。

`MainActivity`：`onOpenConversation` → `taskManager.openConversation(id)` → `route = Chat`。

任务页仍 `listRecent(50)`，不按会话分组，不加耗时/Provider（根 PRD §11.3 缺口不在本刀）。

## Logging

禁止 log `input` / `finalAnswer` / `snapshot_json` / 会话全文。可以 log 任务数、`conversationId` 是否为 default（不要把用户 UUID 当 PII 打到 Logcat 也可以整段不 log id）。

## Rollback

未点「新对话」时所有旧任务都在 `"default"`，窗口应能列出升级前的完成任务（受全表扫描限制，不是 50 条全局 recent）。用户清应用数据会丢 prefs 指针，但任务行仍在，指针回到 `"default"`。
