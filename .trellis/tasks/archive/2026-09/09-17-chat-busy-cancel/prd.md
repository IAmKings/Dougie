# 思考中发送位改为终止

## Goal

任务进行中，输入栏右侧发送位变成终止。点下去取消本轮。

## User value

思考、流式、跑工具、等确认时现在发送钮是灰的，停不下来。要能立刻停掉这一轮。

## Background

- 现网：`inputEnabled == false` 时发送禁用；停止图标只用于 TTS **停止播报**。`TaskManager.cancel()` 已把进行中协程打成 `FAILED` + `任务已取消。` 并写 `endedAt`。对话页未接。
- Chat 的忙碌与 `inputEnabled` 相同：不是 COMPLETED / FAILED / IDLE，**含** `AWAITING_CONFIRMATION`。Confirm 卡「拒绝」只跳过该工具。
- 气泡进入已验收。不 bump DB。无 Compose UI 测试。

## Requirements

- R1 忙碌时发送位为可点终止（`Icons.Filled.Stop`，`contentDescription` 终止）。点 `TaskManager.cancel()`。含思考 / 流式 / 跑工具 / 等确认。
- R2 文本框、附件、麦克风仍随 `inputEnabled` 禁用。空闲、完成、失败仍是发送。完成态 TTS 播报中仍是 **停止播报**（停朗读，不 cancel 已结束的任务）。忙碌优先于播报：若两者同时出现，走终止并顺带停朗读。
- R3 取消后本轮 `FAILED`，助手气泡「任务失败：任务已取消。」，**重试** 可用。不改 `CANCELLED` 文案。不改 Confirm 卡文案。
- R4 `ChatUiState.canCancel` 由 mapper 给出。无 Compose UI 测试。不 bump DB。不改 `listKey` / 进入动效 / `shouldFollowChatFeed`。

## Acceptance Criteria

- [x] AC1 发送后思考或流式中，右钮为终止且可点；点后该轮失败「任务已取消。」，随后可再发送或 **重试**。
- [x] AC2 等确认时同样是终止；点后整轮取消，不是 Confirm「拒绝」。
- [x] AC3 完成后右钮恢复发送；TTS 播报中仍是停止播报。
- [x] AC4 JDK 17：`./gradlew :feature:chat:testDebugUnitTest :core:runtime:test` 通过。无 Compose UI 测试。

## Out of scope

Confirm 上滑动效、改失败文案、History 取消、规则 E、进场动效、DB bump。

## Key Decisions

- 所有忙碌态都给终止，含等确认。
- 图标与 TTS 停止相同，无障碍名称「终止」。
- 取消走现成 `TaskManager.cancel()`，不新开一条失败码。
