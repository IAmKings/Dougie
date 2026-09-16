# Chat 气泡显示耗时

## Goal

对话页每一轮已结束的助手回复能看到这一轮花了多久，不必再切到底栏「任务」。

## User value

任务卡已有墙钟耗时。回看对话、等回复结束时，同样需要「这一轮用了几秒」。

## Background

- 时间戳已在 `snapshot_json`：`TaskManager.submit` 写 `startedAt`；终端 persist / 取消 / `recoverInterrupted` 写 `endedAt`。墙钟含待确认。缺字段的旧快照不编造。不 bump `dougie_tasks.db`。
- `formatTaskDuration` 现只在 `:feature:history`。Chat 现网 `AgentMessage` 只有正文 + 可选「来源：…」+ 末条 **重试** / **播报**。流式中途也会出一条无来源的 `AgentMessage`。
- 根 `PRD.md` §11.3 把耗时写在 Task History；§11.5 Chat Final 没有耗时。本刀只补对话页。

## Requirements

- R1 仅 `COMPLETED` / `FAILED` 且 `startedAt`、`endedAt` 都有时，助手气泡显示耗时。进行中、流式半成品、缺任一时间戳：不显示、不跳秒。
- R2 数字与任务卡同一套：`endedAt - startedAt`；不足1秒 / N秒 / M分 / M分S秒；负间隔按 0。不加「用时」前缀，不加「N小时」。
- R3 只挂在终答或失败句的 `ChatItem.AgentMessage`。不进用户气泡、思考芯片、工具卡、确认卡。过去轮与当前轮终态同一规则。
- R4 画在气泡正下方、来源上方，12sp 次要色；不进气泡正文。**重试** / **播报** 仍在最底。
- R5 映射在 `toChatUiState` / `toPastChatItems`，Compose 不减时间戳。两个 feature 共用同一个 `formatTaskDuration`，不在 Chat 再写一套桶。不 bump DB。不 log 输入 / `snapshot_json`。
- R6 不加 Provider、不加完成时刻、不加 Debug 耗时。不改 codec / 打点。

## Acceptance Criteria

- [x] AC1 新提交且已结束（完成或失败）的轮，助手气泡下出现与任务卡相同的耗时字（如「3秒」）；来源（若有）在它下面。
- [x] AC2 思考 / 确认 / 流式半成品气泡没有耗时。缺 `startedAt` 或 `endedAt` 的旧轮没有耗时。
- [x] AC3 `formatTaskDuration` 桶测仍覆盖 null、不足1秒、N秒、整分、分+秒、负差；History 卡文案不因搬家而变。
- [x] AC4 JDK 17：`./gradlew :core:model:test :feature:chat:testDebugUnitTest :feature:history:testDebugUnitTest` 通过。无 Compose UI 测试。

## Out of scope

进行中跳秒、Chat 上 Provider / 完成时刻、Debug 耗时、小时文案、DB bump、规则 E、共享元素转场、改任务卡文案或布局。

## Key Decisions

- 只在终态显示，不跳秒。
- 文案与任务卡相同，不加「用时」。
- 位置：气泡下、来源上。
- 耗时函数从 `:feature:history` 抽到 `:core:model`，History 改为调用共享函数。
