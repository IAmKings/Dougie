# 任务页搜索与删除单条

## Goal

底栏「任务」能按关键词找出某一轮（含失败），也能删掉单条执行记录。窗口还在；只有非默认窗口被删到一条不剩时，才清名并切回默认。

## User value

默认会话不能整段删除。要能在任务页找到旧卡、丢掉某一次失败或过期的执行，而不必删掉整个「对话 n」。

## Background

- 已归档 Chat 检索：`searchCompletedTurns` + `conversationSearchNeedles`（只已完成 + 终答）。任务页仍 `listRecent(50)` 分组，无搜框，无单条删除。
- 节标题「删除」= 整个非默认窗口。卡片整卡打开对话；「展开」嵌在卡内。
- 无 `deleteByTaskId`。不 bump `dougie_tasks.db`。不改 Chat 切词。记忆 / 审计 / 幂等不 GC。

## Key Decisions

- 同一任务交付：任务页搜索 + 单条删除。
- 空框 = 现网最近 50 条；有关键字 = 全表，切词与 Chat 相同，但**包含失败**（匹配用户句 / 终答 / 失败文案 / 窗口名）。进行中不搜。
- 卡片「展开」旁「删除」+ 确认。默认窗口的卡也能删。节标题「删除」不变。
- 忙时单条删除 no-op。删当前轮则刷新该窗口；非默认窗口因此 0 条 → 清名并 `openConversation(default)`。默认删光 → 空默认。

## Requirements

- R1 顶栏搜索框。空：`listRecent(50)` 分组。非空：全表 decode 过滤，复用 `conversationSearchNeedles`；haystack = `input` + `finalAnswer` + `lastError` + 窗口显示名。`COMPLETED` 与 `FAILED`；其它状态跳过。结果仍 `toHistorySections`。无匹配：中文空态。不 bump DB。不 SQL `LIKE` json。
- R2 每张卡「删除」（`DougieColors.Error`，不打开对话）→ 确认「将删除这一轮，且无法恢复。」确认走 `TaskManager.deleteTask(taskId)`。
- R3 `deleteTask`：busy → null。否则 `DELETE` 该 `task_id`。若该 `conversationId` 再无任务且不是 default：`setTitle("")`，若当前指针是它则切默认并加载。若当前 `_task.taskId` 被删：seed 该窗口剩下最新一条或 null，reload transcript。History `refresh()` + Activity `listRecent(50)`。
- R4 不 log 输入 / 标题 / snapshot。不删 Memory / audit / idempotency。

## Acceptance Criteria

- [x] AC1 空框仍是最近 50 条分组；关键字能命中 50 以外的完成/失败轮；「这个」不会把刘备/咖啡搜出来；点卡仍打开并定位该用户句。
- [x] AC2 确认删除后该卡消失；默认窗口节还在（除非本来没卡）。非默认最后一条删掉后节消失、切回默认。
- [x] AC3 忙时删除不改 store。
- [x] AC4 JDK 17：`:core:runtime:test`、`:feature:history:testDebugUnitTest`、`:app:testPlayDebugUnitTest` 通过。

## Out of scope

Chat 检索改动、分词器、侧栏、删默认窗口、规则 E、DB bump、记忆 GC、左滑删除。
