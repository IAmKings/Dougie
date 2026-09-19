# 工具不确定进度条

## Goal

准备调用 / 正在调用的工具卡按 PRD §11.7 显示不确定进度条。现网只有文案和结果区。High Risk 的步骤名用标题里已有的中文工具名，不另起一行。

## User value

工具还没返回时，卡上能看出「正在干活」，而不是一张静卡。

## Background

- §11.7：不确定进度条；说明栏「High Risk 显示步骤名」。§11.6 进度指示与已落地的 150ms 切换是两行。
- `ToolCallCard`：`PENDING` / `EXECUTING` 标题「准备调用 / 正在调用 {toolDisplayName} ({L0–L4})」，左边 4dp `StatusExecuting`。Confirm 在覆盖层。`LoopEngine` 进入执行后约 280ms `stepDelay`。无步骤名字段。
- Settings 下载是确定进度，Chat 用不确定重载。无 Compose UI 测试。不 bump DB。

## Requirements

- R1 `ToolCard` 在 `PENDING` 或 `EXECUTING` 时，标题下方、终端结果区上方画通栏不确定 `LinearProgressIndicator`，颜色跟执行中色条。`SUCCESS` / `FAILED` 不画。
- R2 所有风险等级同样有条。不另起「步骤：」行；标题里的中文工具名就是 High Risk 步骤名。
- R3 Confirm 覆盖层不画条。打开已完成窗口或底栏返回：完成链无条。执行中途回来：条在，不算重播进入。动效期间可终止。系统动画时长 0：条仍在，不自己加位移或假百分比。
- R4 不改文案、`listKey`、150ms 切换、Confirm 上滑/压暗、终止语义、`shouldFollowChatFeed`。History **展开** 不画条。无 Compose UI 测试。不 bump DB。

## Acceptance Criteria

- [x] AC1 「现在几点了？」执行中：工具卡出现不确定进度条；变成「已调用」后条消失。
- [x] AC2 「打开微信」确认之后执行中：同样有条；覆盖层等待确认时没有条。L2+ 不另起步骤行。
- [x] AC3 打开已完成的窗口或底栏回对话：已调用的卡没有条。系统关闭动画时条仍在、不位移。
- [x] AC4 JDK 17：`./gradlew :feature:chat:testDebugUnitTest` 通过。无 Compose UI 测试。

## Out of scope

Chat↔任务页共享元素、Confirm 离场、确定进度/百分比、另写步骤行或后果说明、思考芯片进度、History 进度条、规则 E、DB bump。

## Key Decisions

- 所有准备/正在调用的 `ToolCard` 都有不确定条。
- High Risk 步骤名 = 标题里的 `toolDisplayName`，不加第二行。
- 条在标题和结果区之间。
