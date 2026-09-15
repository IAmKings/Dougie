# 任务卡显示耗时与 Provider

## Goal

底栏「任务」每张卡在现有摘要、状态、循环、工具链、错误之外，再显示这一轮的 **耗时** 和 **Provider**（对齐根 `PRD.md` §11.3）。回看时能看出走了本地意图 / 本地 LLM / 云端，以及大概花了多久，不必进开发者页。

## Background

- 现网 `HistoryCard`：摘要、状态徽标、`循环 n · toolChain`、失败 `error`。没有耗时、没有 Provider。
- `AgentTask.completionPath` 已有并已进 `TaskSnapshotCodec`。开发者页：本地意图 / 本地 LLM / 远程 LLM / 无。
- `AgentTask` 无开始/结束时间。`agent_tasks.updated_at` 是最后一次 upsert，不能当耗时。Codec `ignoreUnknownKeys`；`dougie_tasks.db` onUpgrade 仍 drop，本刀不 bump。
- `priorTurns` 仍不进 snapshot。不 log 输入 / `snapshot_json`。
- 本刀不展开 Loop / Tool Result。

## Requirements

- R1 新 `TaskManager.submit` 的任务在 snapshot 里有 `startedAt`（epoch ms）。进入 `COMPLETED` / `FAILED`（含取消、`recoverInterrupted`）时有 `endedAt`。`copy()` 保留已有时间戳；已有 `endedAt` 不覆盖。不 bump DB、不加 SQL 列。
- R2 任务卡 Provider 文案与开发者页同一套：本地意图 / 本地 LLM / 远程 LLM。`completionPath == null` 时不显示 Provider（不写「无」）。
- R3 任务卡耗时 = `endedAt - startedAt`（墙钟，含待确认停留）。缺任一时间戳、或旧快照无字段 → 不显示耗时，不编造、不用 `updated_at`。
- R4 耗时文案：`<1s` → 「不足1秒」；`1–59s` → 「N秒」；`≥60s` 整分 → 「M分」；有余秒 → 「M分S秒」。负间隔按 0 处理。
- R5 `priorTurns` 仍不进 snapshot。不 log 输入。History 不解码 `snapshot_json`（继续 `AgentTask.toHistoryItem`）。

## Acceptance Criteria

- [x] AC1 新提交且已结束的任务，卡片显示耗时；墙钟从点发送到完成/失败（含确认等待）。JVM：`submit` 后 `startedAt != null`；终端态 persist 后 `endedAt >= startedAt`。
- [x] AC2 `completionPath` 为 `LOCAL_INTENT` / `LOCAL_LLM` / `REMOTE_LLM` 时卡片显示对应中文；为 null 时没有 Provider 字样、也不显示「无」。
- [x] AC3 缺 `startedAt` 或 `endedAt` 的旧快照不显示耗时。
- [x] AC4 取消与启动恢复中断：终端 FAILED 快照带 `endedAt`（若当时已有 `startedAt` 则可算出耗时）。
- [x] AC5 `TaskSnapshotCodec` 编解码 `startedAt`/`endedAt`；缺键为 null；`priorTurns` 仍省略。不 bump `dougie_tasks.db`。
- [x] AC6 `./gradlew :core:model:test :core:runtime:test :feature:history:testDebugUnitTest :feature:debug:testDebugUnitTest`（JDK 17）通过。

## Out of scope

可展开 Loop / Tool Result、会话删除、侧栏、规则 E、Chat 气泡耗时、开发者页耗时、用 `updated_at` 冒充耗时、小时级文案（「N小时」）、DB schema bump。

## Key Decisions

- Provider 只用已有 `completionPath`，不另存 Provider 字符串。
- 耗时用 snapshot `startedAt` / `endedAt`，不用表列 `updated_at`。
- 墙钟：从点发送到完成/失败，含待确认等待。
- 卡片一行元信息：`listOfNotNull(duration, provider).joinToString(" · ")`；两者皆空则不画该行。
- 进行中任务可已有 Provider、尚无 `endedAt` → 只显示 Provider、不显示耗时。
- `endedAt` 在 `TaskManager` persist / 取消路径以及 `recoverInterrupted` 打点，不改 LoopEngine 每一处 `copy(status=…)`。
- 中文 Provider 标签抽到 `CompletionPath.toUserLabel()`，Debug 的「无」仍只给 null。
