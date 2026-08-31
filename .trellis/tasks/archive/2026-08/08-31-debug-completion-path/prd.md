# 开发者页显示本轮处理路径

## Goal

设置 → 开发者的当前任务卡片能看出**这一轮实际怎么结束的**：本地意图短路径，还是远程 LLM。用来区分「现在几点走了 MiniRBT」和「静默进了云端」。

## Background

- 开发者页只展示 `taskId` / `status` / `loopCount` / `lastError` 与审计工具名（`:feature:debug`）。
- 意图短路径成功时 `AuditLog` 只有 `time`/`battery`，看不出跳过了 LLM。
- 分类原文、intent 标签、Prompt 不得进 Debug / `AuditLog` / Logcat。
- `agent_tasks.snapshot_json` 由 `TaskSnapshotCodec` 编解码；新字段可可选，旧行解码为未设置。

## Requirements

- R1 当前任务卡片增加一行处理路径，仅反映**本轮实际分支**：
  - `本地意图`：`completeFromIntentIfMatched` 成功（或该路径上的失败，只要没进 LLM 循环）。
  - `远程 LLM`：未走通短路径后进入 `collectLlmTurn` / `EgressGateway`（含超时、空回复、出境拦截等失败）。
- R2 未进入上述任一分支前（如 `PREPARING`）显示 `无`。
- R3 不展示意图包安装状态（设置离线模型行已覆盖）。
- R4 路径枚举进 `AgentTask`，经 `toDebugTaskSnapshot` 映射中文；不映射 `input` / Prompt / intent / slots。
- R5 `TaskSnapshotCodec` 编解码该字段；旧快照缺字段 → `无`。
- R6 端侧生成式 LLM 以后再加枚举值，本任务不预留 UI 空档。

## Acceptance Criteria

- [ ] AC1 高置信时间/电量短路径完成后，开发者当前任务为 `本地意图`。
- [ ] AC2 短路径未命中并走云端（成功或 LLM 超时等）显示 `远程 LLM`。
- [ ] AC3 Debug 快照字符串不含用户输入、intent 名、Prompt。
- [ ] AC4 聊天气泡、通知、审计表结构不变。
- [ ] AC5 `:feature:debug` 与 `:core:runtime` 相关单测覆盖 R1–R3。

## Out of scope

- 审计行新列、任务列表（历史）单独展示路径。
- 聊天 UI 标注本地/云端。
- 本轮实现端侧 LLM 路径值。
- Logcat 打印路径以外的分类细节。

## Technical notes

- `LoopEngine`：短路径 return 前写本地意图；进入 `while`/`collectLlmTurn` 前写远程 LLM。
- 默认 `AgentTask` 字段为 null / 未设置。
- 不改 `AuditLog.record` 签名。
