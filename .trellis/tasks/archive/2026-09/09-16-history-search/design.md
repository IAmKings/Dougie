# Design: Chat 检索历史对话

## Boundary

| 模块 | 职责 |
|------|------|
| `:core:model` | `ConversationHit`；`AgentTask.retrievedConversationHits` |
| `:core:runtime` `TaskStore` | `searchCompletedTurns(query, excludeTaskIds, limit)` 全表扫 + needles |
| `LoopEngine` | `attachPriorTurns` 后检索、预算、打来源标签 |
| `:core:llm` `ChatPromptAssembler` | `systemPrefix` 增加「相关历史对话」 |
| `:feature:chat` | `citationSources` 合并 `sourceLabel` |
| `:data:tasks` `SqliteTaskStore` | 与 `listByConversation` 同款扫 snapshot，内存过滤 |

`:core:*` JVM 纯净。不 bump `dougie_tasks.db`。

## Store contract

```text
suspend fun searchCompletedTurns(
  query: String,
  excludeTaskIds: Set<String> = emptySet(),
  limit: Int = 3,
): List<AgentTask>
```

- needles = `searchNeedles(query)`（`:core:memory`，runtime 已依赖）。空 needles → 空列表。
- 只收 `COMPLETED`、`finalAnswer` 非空白、`taskId` 不在 exclude。
- haystack = `input` + `finalAnswer`（忽略大小写 contains）。**不读** `toolTrace` / `argsSummary` / `resultJson`。
- 顺序：与 `listRecent` 相同的新→旧，take `limit`。
- SQLite：`SELECT task_id, snapshot_json FROM agent_tasks ORDER BY updated_at DESC`，decode 失败 skip。禁止 `snapshot_json LIKE`。
- 空 IN/空表 → 空列表，不抛。

## Loop

现网顺序保持：`retrieveMemories`（可被记忆开关跳过）→ 短路径 → `attachPriorTurns` → **本刀检索** → LLM while。

exclude = `{task.taskId}` ∪ priorTurns 对应的已完成 `taskId`（用 `listByConversation` 已加载的 rows 对齐，不要再扫一次）。标题：`conversationDisplayName(id, titles[id], if default 「默认会话」 else 「对话」)`，禁止 UUID。

`ConversationHit(taskId, conversationId, sourceLabel, user, assistant)` 截断后写入 `retrievedConversationHits` 并 `emit`。预算：最多 3 条；累计 `user.length+assistant.length` ≤800。

短路径（说话 / 匹配点击 / 开 App / MiniRBT）在 `attachPriorTurns` 之前 return，不跑本检索。

## Prompt

`systemPrefix` 在 Known facts 之后：

```text
相关历史对话：
- {sourceLabel}
  用户：{user}
  助手：{assistant}
```

无命中不加该段。`localPrompt` 走同一 `systemPrefix`，故端侧自动带上。云端 `OpenAICompatibleProvider` 已用 `systemPrefix`。`priorTurns` 的「近期对话」块不变。

## Persistence / UI

Codec 编解码 `retrievedConversationHits`（缺字段 = empty）。不要把 `priorTurns` 写进 snapshot。

Chat `citationSources()`：先记忆 `source`，再历史 `sourceLabel`，trim、去重、保序。不可点。

Debug 映射不增加命中正文。History 卡不展示命中。

## Don't

- DB bump / FTS 虚表 / SQL LIKE json。
- 命中 upsert 进 `memory_facts`。
- log query 或命中正文。
- 空检索改写为「未找到」。
- 任务页搜索框。
