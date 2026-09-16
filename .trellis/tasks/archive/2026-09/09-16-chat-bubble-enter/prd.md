# Chat 气泡进入动效

## Goal

对话里新插入的用户句、思考芯片、工具卡、助手回复按 PRD §11.7 进入：200ms FastOutSlowIn，淡入并上移 8dp。打开已有窗口或从底栏回来时，已在列表里的条目不重播。

## User value

发送后新气泡不再「啪」地贴上列表；回看旧对话时也不会整屏闪一遍进入。

## Background

- 根 `PRD.md` §11.7：Bubble 进入 200ms `FastOutSlowIn`，淡入 + 上移 8dp；动效期间可交互；系统「移除动画」时降级为淡入。同表 Confirm 弹出、状态切换、共享元素另有行，本刀不做。
- `ChatFeed` `LazyColumn` 已有稳定 `listKey`。流式与终态助手气泡共用 `{taskId}:agent`。现网无进入淡入。`FastOutSlowInEasing` 只用于语音 overlay。
- `ChatRoute` 仅在 `AppRoute.Chat` 时组合；去任务/设置会卸掉。`ChatViewModel` 仍是 Activity 作用域，`shouldFollowChatFeed` / `pendingFocusKey` 必须不被进入动画带跑。
- 无 Compose UI 测试。不 bump DB。

## Requirements

- R1 新插入且种类为 `UserMessage` / `Thinking` / `ToolCard` / `AgentMessage` 的条目：200ms FastOutSlowIn，淡入 + 上移 8dp。`ConfirmCard` 不播进入（留给 §11.7 Confirm 上滑）。
- R2 只播尚未见过的 `listKey`。同一 key 的正文更新（流式助手气泡、耗时出现）不重播。`ChatFeed` 第一次组合（打开窗口、底栏回对话、History 点卡回来）把当前 key 全部记为已见，不播。列表被清空（新对话）后，下一轮新 key 再播。
- R3 动效期间可点 **重试** / **播报** / 发送，不锁列表。系统动画时长为 0（移除动画）时只淡入、不上移。
- R4 不改 `listKey`、不改 `shouldFollowChatFeed` / `pendingFocusKey`。进入是否播放用纯函数决定，JVM 单测覆盖；无 Compose UI 测试。不 bump DB。不 log 输入。

## Acceptance Criteria

- [x] AC1 空窗口发送：用户气泡和随后出现的思考 / 工具 / 助手各播一次进入；Confirm 不播这套淡入上移。
- [x] AC2 打开已有窗口、底栏回对话、从任务卡定位回来：已有条目不重播。流式助手变成终态（含耗时）不重播。
- [x] AC3 系统关闭动画时新条目只淡入。动效中仍可点气泡下的 **播报** / **重试**。
- [x] AC4 JDK 17：`./gradlew :feature:chat:testDebugUnitTest` 通过。无 Compose UI 测试。

## Out of scope

Thinking→Tool 150ms 状态切换、Confirm 底部上滑 + 压暗、Chat↔任务页共享元素、History 列表动效、规则 E、改 `listKey`、DB bump。

## Key Decisions

- 用户 / 思考 / 工具 / 助手播进入；Confirm 本刀不播。
- 只播新插入的 key；卸掉再进 Chat、或第一次画出整窗，不重播。
- 文案与时长按 §11.7，不加「用时」类新字。
