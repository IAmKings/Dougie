# Implement: 任务页按窗口分组并定位语句

## Order

1. `:feature:history`：`toHistorySections` + `HistoryUiState.sections`；`HistoryItemTest`（两窗口、default 标题、节序）。
2. `HistoryScreen`：`stickyHeader` 分节；`onOpenConversation: (conversationId, taskId) -> Unit`。
3. `:feature:chat`：`pendingFocusKey`；`shouldFollowChatFeed` 在有 focus 时不滚到底；key 出现后 `scrollToItem`。单测 focus 优先于 follow。
4. `:app` `MainActivity`：busy 守卫下 `openConversation` + `requestFocus` + `route = Chat`。
5. spec：`state-management.md`（任务页分节 + 点卡定位）；`hook-guidelines.md`（focus 覆盖 follow-to-end）；`directory-structure.md` history 行。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:history:testDebugUnitTest :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest
```

JDK 17。真机：两窗口各两轮 → 两节；点旧窗口非最后一轮 → 停在那句；底栏回对话不跳那句；忙时点卡不切。

## Risk / rollback

- 换窗口仍 `shouldFollow` 到底会盖掉 focus → 必须 pending focus 优先。
- `stickyHeader` 与现有 `contentPadding` 需真机看一眼吸顶是否挡住第一张卡。
- 编号随 50 条窗口进出可能从「对话 3」变成「对话 2」——接受，不持久化标题。

## Do not

- `conversations` 表、SQL 列、折叠、改 LLM、`listByConversation` 冒充 listRecent。
- 把 focus 写入 `snapshot_json` / prefs。
