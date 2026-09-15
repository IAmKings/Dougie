# Implement: 默认会话 + 新对话 + 窗口多轮

## Order

1. `:core:model`：`ConversationIds.DEFAULT` + `AgentTask.conversationId`。现有 `AgentTask(...)` 调用靠默认参数，不必全仓库改字面量。
2. `:core:runtime`：codec 编解码；`ConversationPointer`；`TaskStore.listByConversation` + `InMemoryTaskStore`；`TaskManager` transcript / `newConversation` / `openConversation` / submit 写入 id。单测：缺字段 → `default`；两轮同会话 list；新会话空 transcript；忙时 open 被拒；`recoverInterrupted` 仍不调 LLM。
3. `:data:tasks`：`SqliteTaskStore.listByConversation` 全表 ASC + 内存过滤。不改 `DB_VERSION`。
4. `:data:preferences`：`current_conversation_id`；`save()` 不得读写该 key。
5. `:feature:chat`：`toPastChatItems` + `combine` 映射；顶栏「新对话」确认。`ChatUiStateTest` 多轮。
6. `:feature:history`：`HistoryItem.conversationId`；卡片点击。`HistoryItemTest`。
7. `:app`：`TaskManager` 注入 pointer；冷启动 refresh transcript + 有条件 seed；History 回调 `openConversation`。
8. spec：`state-management.md`（Chat 数据源 = task + transcript）；`hook-guidelines.md`（Chat 可经 TaskStore/TaskManager 读列表，仍不打开 SQLite）；`database-guidelines.md`（按会话列出靠扫描快照，不加列）。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :core:model:test :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :feature:history:testDebugUnitTest :app:testPlayDebugUnitTest
```

JDK 17。真机：连发两轮看四条气泡；新对话后窗口空再发，任务页仍有旧两条；点旧任务回到整段（含未被点中的那一轮）；进行中点任务页不切走。

## Risk / rollback

- `listRecent(50)` 过滤当当前会话 → 新对话后旧轮消失。必须 `listByConversation` 全表。
- `ProviderSettings.save` 误清会话指针 → 独立 key。
- 把 live 任务再塞进 transcript 会重复气泡。拼装时排除 `task.taskId`。
- 改 `onUpgrade` drop 会清 v0.1.1 任务库。本刀不加列。

## Do not

- 改 LLM `messages` / `localPrompt`。
- `conversations` 表、任务页分组、滚到被点气泡、对话页最近会话。
- `task.py start` 本刀前未获规划批准。
