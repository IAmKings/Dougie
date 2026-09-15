# 删除整个会话窗口

## Goal

从底栏「任务」删掉一个非默认对话窗口：该窗口的全部执行记录从任务页和 Chat 消失，自定义名一并清掉。默认会话不能删。若删的是当前窗口，切回默认会话。

## User value

能新建、能改名之后，还能丢掉不要的窗口，避免旧「对话 n」一直占着任务页。

## Background

- 无会话表。窗口 = `snapshot_json.conversationId` + `conversation_titles_json` + 当前指针。
- `TaskStore` 尚无删除 API。SQLite 无 `conversation_id` 列，只能扫 snapshot 后按 `task_id` 删。不 bump DB。`listByConversation` 已全表扫描。
- 默认 id 为 `"default"`。忙时不能 `newConversation` / `openConversation`。History 节标题已有「改名」。
- 记忆 / 审计 / 幂等不按会话 GC。本刀不扫它们。

## Requirements

- R1 非默认节标题「改名」旁有「删除」（危险色）。默认会话没有该按钮。
- R2 点「删除」出确认框：带当前显示名，说明不可恢复；确认才删，取消关闭。
- R3 确认后：删除该 `conversationId` 在 `agent_tasks` 里的全部行（全表，不只 50 条）；`setTitle(id, "")`；History `refresh()`。不 bump DB。
- R4 若删的是当前窗口：指针改为 `default` 并 `openConversation(default)`（加载默认 transcript）。否则 Chat 当前窗口不动。
- R5 `TaskManager.isBusy()` 时删除 no-op（不弹确认或弹了确认也不执行），与不能切窗口相同。
- R6 不 log 标题 / 输入 / snapshot。不删 Memory / audit / idempotency。

## Acceptance Criteria

- [x] AC1 非默认窗口确认删除后，任务页不再出现该节；该 id 的 `listByConversation` 为空；自定义名被清掉。
- [x] AC2 默认会话节没有「删除」；误调 `deleteConversation("default")` 不删行。
- [x] AC3 删除当前非默认窗口后，当前 id 为 `default`，Chat 显示默认会话内容（或空默认）。
- [x] AC4 忙时删除不改变 store。
- [x] AC5 JDK 17：`:core:runtime:test`、`:feature:history:testDebugUnitTest`、`:app:testPlayDebugUnitTest`（若 MainActivity 接线有测）通过。

## Out of scope

删默认会话、侧栏、Chat 顶栏删除、单条任务删除、记忆 GC、DB bump、规则 E。

## Key Decisions

- 只能删非默认；删当前则切回默认。
- 入口 = 节标题「删除」+ 确认框；忙时一律 no-op。
- 全表扫描删除，不 bump schema。
