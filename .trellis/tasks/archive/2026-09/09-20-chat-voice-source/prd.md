# 用户气泡语音来源标注

## Goal

用语音转写发出的用户气泡，在气泡外显示「语音转写」来源标注；纯键盘发送不加标注。

## User value

回看对话时能分清哪一句是说出来的、哪一句是打的，而不必猜。

## Background

- 根 `PRD.md` §11.5 User 行：内容为用户文本 / 语音转写；验收点「语音消息显示来源标注」。
- 现网 `UserBubble` 只有 `text`。ASR 成功写入草稿并置 `voiceUsedThisDraft`；发送时 `speakReply = voiceUsedThisDraft`。`speakReply` 已在 `AgentTask` / `TaskSnapshotCodec` 里（可选字段，不 bump SQLite）。重试会拷贝该旗标。
- Agent 气泡已有 12sp `OnSurfaceVariant` 的耗时和「来源：」。用户气泡没有对应标注。
- Chat↔History `sharedBounds` 打在用户气泡本体上。

## Requirements

- R1 `ChatItem.UserMessage` 增加可空展示字段（推荐 `sourceLabel`）。`toChatUiState` / `toPastChatItems`：`speakReply == true` → `"语音转写"`，否则 null。不新增持久化字段，不 bump DB。
- R2 `UserBubble` 在气泡**下方**、右对齐画该标注：12sp `OnSurfaceVariant`，不进气泡正文，不用等宽。纯键盘发送无此行。
- R3 `sharedBounds` 仍只包气泡文本，不含标注。不改 `listKey`、进入动效、ASR 插入、TTS 自动播报语义。
- R4 不把 utterance 打进 Logcat；标注是固定产品文案，不是转写原文。

## Out of scope

- 改 ASR / TTS / `voiceUsedThisDraft` 何时置位。
- 工具卡、确认卡、任务页、打字机。
- 为「说完又删光再手打」单独清旗标（沿用现网 `speakReply`）。

## Technical notes

- JVM：`ChatUiStateTest` 覆盖 live / past / `speakReply` false。无 Compose UI 测试。

## Acceptance Criteria

- [x] AC1 本轮 `speakReply=true`：用户气泡下出现「语音转写」；`false` 没有该行。
- [x] AC2 历史轮同样按该任务的 `speakReply` 映射；重试后仍有标注。
- [x] AC3 标注不在气泡 `text` 里、不进 `listKey`、不包进 `sharedBounds`。
- [x] AC4 `ChatUiStateTest` 覆盖上述映射；`./gradlew :feature:chat:testDebugUnitTest` 过。
