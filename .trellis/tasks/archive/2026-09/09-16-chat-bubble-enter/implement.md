# Implement: Chat 气泡进入动效

## Order

1. `nextChatItemEnter` + `ChatUiStateTest`（首帧、清空、新 thinking、Confirm 不播、同一 agent key 不重播）。
2. `ChatFeed`：remember seen；对 playKeys 做 200ms FastOutSlowIn 淡入+上移 8dp；Confirm 不包。
3. `ANIMATOR_DURATION_SCALE == 0f` 只淡入。
4. 不改 `listKey` / follow / focus。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:chat:testDebugUnitTest
```

JDK 17。真机：空窗发送有进入；打开旧窗、底栏回对话不重播；流式变终态不重播；Confirm 无这套位移；系统「移除动画」时只淡入。

## Risky files

- `feature/chat/src/main/kotlin/com/dougie/feature/chat/ChatScreen.kt`（`ChatFeed`）
- `feature/chat/src/main/kotlin/com/dougie/feature/chat/ChatUiState.kt`

## Do not

- Confirm 上滑、状态切换、共享元素、History 动效。
- `task.py start` 未获规划摘要批准前改产品代码。
