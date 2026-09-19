# Confirm 卡离场动效

## Goal

确认 / 拒绝 / 终止（含 60s 超时当拒绝）后，Confirm 覆盖层按弹出的反向收起：250ms FastOutSlowIn 下滑，压暗淡出。现网是瞬间卸掉。确认之后工具卡仍可同时 150ms 淡入。

## User value

确认步骤有来有回，按完按钮卡会收下去，而不是蒸发。

## Background

- 弹出已落地：覆盖对话区，250ms 上滑 + 压暗；点压暗不关；第一次组合不重播。`chatConfirmCard` 变 `null` 后 overlay 不再组合。确认/拒绝/`confirmTimeoutMs` 走 TaskManager；终止取消整轮。
- 确认后列表立刻出现 `ToolCard`（150ms 淡入）。底栏离开卸掉 `ChatRoute`。无 Compose UI 测试。不 bump DB。

## Requirements

- R1 Confirm 从有到无（确认、拒绝、终止、超时拒绝）：覆盖层留着播 250ms FastOutSlowIn 下滑，压暗一起淡出。不推迟 `TaskManager` 确认/拒绝/取消。工具卡可以同时淡入。
- R2 点压暗仍不关。进场规则不变。`ChatRoute` 第一次组合或底栏回来时：不播离场。系统动画时长 0：只淡出、不下滑。
- R3 不改文案、风险色、拒绝/终止语义、`listKey`、气泡进入、150ms 切换、进度条、Chat↔任务转场。无 Compose UI 测试。不 bump DB。

## Acceptance Criteria

- [x] AC1 确认或拒绝：卡下滑收起、压暗淡出；确认后工具卡可以一边出现。拒绝语义仍是跳过该工具。
- [x] AC2 等确认时按终止（或等到超时拒绝）：同样下滑收起；任务失败/取消语义与现网相同。点压暗仍不关。
- [x] AC3 打开已在等确认的窗口或底栏回对话：不播离场。系统关动画时只淡出。
- [x] AC4 JDK 17：`./gradlew :feature:chat:testDebugUnitTest` 通过。无 Compose UI 测试。

## Out of scope

超时倒计时 UI、点压暗=拒绝、Predictive Back、规则 E、DB bump。

## Key Decisions

- 确认 / 拒绝 / 终止 / 超时拒绝都播同一套离场。
- 不把 TaskManager 调用拖到动效结束。
- 工具卡淡入与离场重叠。
- 离开 Chat 不播离场。
