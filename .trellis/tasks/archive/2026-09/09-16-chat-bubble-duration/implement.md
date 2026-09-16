# Implement: Chat 气泡显示耗时

## Order

1. 把 `formatTaskDuration` 迁到 `:core:model`；`AgentTaskTest` 接过桶测（null / 不足1秒 / 3秒 / 59秒 / 1分 / 1分12秒 / 负差）。
2. History 改为调用共享函数；删本地副本；`HistoryItemTest` 仍覆盖 `toHistoryItem().durationLabel`。
3. `ChatItem.AgentMessage.durationLabel`；`toChatUiState` / `toPastChatItems` 仅终态写入。
4. `ChatUiStateTest`：完成+两端时间戳、失败+两端、流式、缺字段、`toPastChatItems`。
5. `AgentBubble`：气泡下、来源上，12sp `OnSurfaceVariant`；无 Compose UI 测试。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :core:model:test :feature:chat:testDebugUnitTest :feature:history:testDebugUnitTest
```

JDK 17。真机：新完成一轮，对话里助手气泡下有与任务卡相同的「N秒」；思考/流式时没有；旧轮无时间戳则没有。

## Risky files

- `feature/chat/src/main/kotlin/com/dougie/feature/chat/ChatUiState.kt`
- `feature/chat/src/main/kotlin/com/dougie/feature/chat/ChatScreen.kt`（`AgentBubble`）
- `feature/history/src/main/kotlin/com/dougie/feature/history/HistoryItem.kt`（删副本）

## Do not

- bump DB、改 codec / `startedAt` 打点。
- 跳秒、Provider、完成时刻、Debug。
- `task.py start` 未获规划摘要批准前改产品代码。
