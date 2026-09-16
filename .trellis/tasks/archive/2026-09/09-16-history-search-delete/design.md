# Design: 任务页搜索与单条删除

## Boundary

| 模块 | 职责 |
|------|------|
| `TaskStore.searchHistory(query, limit)` | 全表 newest-first；`conversationSearchNeedles`；COMPLETED/FAILED；haystack input+finalAnswer+lastError |
| ViewModel | 空 query → `listRecent(50)`；非空 → searchHistory，再用窗口名补过滤（store 看不到 prefs 标题） |
| `TaskStore.deleteByTaskId` | 按主键删一行 |
| `TaskManager.deleteTask` | busy no-op；删行；空非默认窗口清名并切默认；必要时 seed/reload |
| History 卡 | 「删除」+ 确认；不 `openConversation` |
| `:app` | busy 门、join、refresh History + `listRecent` 快照 |

不 bump DB。Chat `searchCompletedTurns` 不改。

## Search

SQLite：与 `searchCompletedTurns` 同款 `ORDER BY updated_at DESC` 扫 snapshot，Kotlin 过滤。标题匹配在 ViewModel：needles 任一命中 `section.title` 则保留该卡（或先按 store 命中，再把「仅标题命中」的同窗卡并入——更简单：store 命中 ∪ 标题命中的任务。标题命中：该 `conversationId` 的全部终态卡都列出可能太宽。

约定：窗口名匹配则该 id 下终态任务都进入结果（用户搜「工作」想看到那一节）。store 文本匹配则只进命中的那些卡。ViewModel 合并去重。

空 needles（只有「这个」）→ 空结果，不是 50 条。

## Delete

`deleteByTaskId`：blank → 0；否则 `DELETE FROM agent_tasks WHERE task_id=?`。InMemory 同步 `recentIds`。

`deleteTask` 在 dispatcher：再读 `listByConversation` 判空。

## UI

搜索框在「任务历史」标题下，单行。卡片行：「展开」旁「删除」。确认标题「删除任务」，正文不可恢复。无匹配文案：「没有匹配的任务」。

## Don't

- 改 Chat needles / `searchCompletedTurns`。
- 节标题「删除」改成删单条。
- log query。
