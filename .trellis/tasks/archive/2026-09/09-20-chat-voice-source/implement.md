# Implement: 用户气泡语音来源标注

## Order

1. `ChatItem.UserMessage.sourceLabel`；`toChatUiState` / `toPastChatItems` 按 `speakReply` 填 `"语音转写"`。
2. `ChatUiStateTest`：true/false、past、retry 映射；`text` / `listKey` 不含该文案。
3. `UserBubble` 增加可选 caption；调用处传入 `item.sourceLabel`；`sharedBounds` 仍只在气泡 Text。
4. `./gradlew :feature:chat:testDebugUnitTest`

## Do not

- 改 ASR、TTS、`TaskSnapshotCodec`、DB。
- 把 caption 包进 `sharedBounds` 或写进 `listKey`。
- Compose UI 测试。
- `task.py start` 前未获规划批准。
