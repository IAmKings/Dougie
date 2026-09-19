# Chat↔任务页共享元素转场

## Goal

点任务卡进入那一轮对话时，卡和对应 Chat 用户气泡按 PRD §11.7 做 300ms 共享元素；底栏和系统返回的 Chat↔任务页做 300ms 淡入。现网是立刻换屏。

## User value

从任务列表进到那句话时，卡会接到气泡上，底栏来回也不再硬切。

## Background

- §11.7：300ms 共享元素，Chat ↔ Task History；可交互；系统关动画时淡入。
- `MainActivity` `when (route)` 互斥组合，去任务卸掉 `ChatRoute`。卡点击：`openConversation` + `requestFocus` + `AppRoute.Chat`，滚到 `{taskId}:user`。底栏只改路由。忙碌不打开。`consumeBack`：History→Chat。不要 Predictive Back 自定义。无 Navigation Compose。
- BOM `2024.12.01` 有 `SharedTransitionLayout`（Experimental）。History 卡 `taskId`；Chat `UserMessage.listKey` 为 `{taskId}:user`。无 Compose UI 测试。不 bump DB。

## Requirements

- R1 点任务卡（非忙碌）：该卡与 Chat 里同一 `taskId` 的用户气泡 300ms 共享边界。展开 / 删除不走这条。
- R2 底栏「任务」「对话」、以及系统/工具栏返回 History↔Chat：300ms 淡入淡出，不指定共享元素。Settings / 记忆 / 权限 / Debug 仍立刻换屏。
- R3 动效期间可点。系统动画时长 0：只淡入，不做边界位移。忙碌守卫、`requestFocus` / `shouldFollowChatFeed` / `listKey` 语义不变。
- R4 不改气泡进入、Confirm、进度条、终止。不要 Predictive Back 自定义、不要 Navigation Compose。无 Compose UI 测试。不 bump DB。

## Acceptance Criteria

- [x] AC1 点一张已完成任务卡：卡接到该轮用户气泡，约 300ms；Chat 仍滚到那句用户话。忙碌时仍不打开。
- [x] AC2 底栏对话↔任务、系统返回任务→对话：300ms 淡入，不是立刻硬切。进设置仍立刻换。
- [x] AC3 系统关闭动画：只有淡入，卡不飞向气泡。
- [x] AC4 JDK 17：`./gradlew :app:testPlayDebugUnitTest :feature:chat:testDebugUnitTest :feature:history:testDebugUnitTest` 通过。无 Compose UI 测试。

## Out of scope

Predictive Back 自定义、Navigation Compose、记忆/设置转场、反向「最后一句气泡飞回某张卡」、规则 E、DB bump。

## Key Decisions

- 卡点击：卡 ↔ `{taskId}` 用户气泡共享。
- 底栏 / 返回：300ms 淡入。
- 其他路由维持立刻切换。
