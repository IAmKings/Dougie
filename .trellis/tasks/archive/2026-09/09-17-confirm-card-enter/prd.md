# Confirm 卡弹出动效

## Goal

高风险工具确认卡按 PRD §11.7 弹出：250ms FastOutSlowIn，从底部上滑，对话列表压暗。现网是列表里直接出现一张卡。

## User value

确认步骤要像真正的弹出，压暗背后的对话，避免当成普通工具卡划过去。

## Background

- §11.7：250ms `FastOutSlowIn`，底部上滑 + 背景压暗；动效期间可交互；系统「移除动画」时降级为淡入。§11.8 Confirm「可覆盖」气泡列表。
- 现网 `ConfirmToolCard` 在 `ChatFeed` 里；气泡进入不播 Confirm。确认/拒绝走 `TaskManager`。输入栏终止取消整轮。预览图已是全屏覆盖；本刀只盖 `weight(1f)` 对话区，输入栏仍在。
- 打开窗口会卸掉 `ChatRoute`。无 Compose UI 测试。不 bump DB。

## Requirements

- R1 Confirm 从对话列表拿出来，盖在列表上（顶栏、输入栏、底栏不盖）。背后压暗。列表里不再同时画这张卡。
- R2 新出现的 Confirm：250ms FastOutSlowIn，从该覆盖层底部上滑到位。点压暗区域不关闭、不等于拒绝或终止。确认 / 拒绝 / 终止在动效中仍可点。
- R3 `ChatRoute` 第一次组合时若已在等确认：不重播弹出（底栏返回、任务卡回来）。系统动画时长为 0：只淡入卡和压暗，不上滑。
- R4 不改 Confirm 文案、风险色、拒绝=跳过该工具、终止=取消整轮。不改 `listKey`、气泡进入、`shouldFollowChatFeed`。无 Compose UI 测试。不 bump DB。

## Acceptance Criteria

- [x] AC1 任务进入等确认：列表压暗，卡从底部滑出；列表里没有第二张同样的卡。确认/拒绝语义与现网相同。
- [x] AC2 点压暗区域卡不消失、任务仍等确认。输入栏终止仍取消整轮。
- [x] AC3 打开已在等确认的窗口，或底栏回对话：卡已在，不重播上滑。系统关闭动画时只淡入。
- [x] AC4 JDK 17：`./gradlew :feature:chat:testDebugUnitTest` 通过。无 Compose UI 测试。

## Out of scope

思考→工具 150ms 切换、关闭/确认后的离场动效、点压暗=拒绝、超时未操作视为拒绝、Chat↔任务页共享元素、规则 E、DB bump。

## Key Decisions

- 覆盖对话列表，不是列表内上滑。
- 点压暗不关掉。
- 第一次画出已有 Confirm 不重播。
