# Implement: 任务页搜索与单条删除

## Order

1. `TaskStore.searchHistory` + `deleteByTaskId`（InMemory + Sqlite）；单测：失败可搜、完成可搜、进行中不搜、needles 空、UNO 不误伤闲聊、delete 主键。
2. `TaskManager.deleteTask`：busy no-op；空非默认切默认；当前 task 被删则 seed。`ConversationTaskManagerTest`。
3. HistoryViewModel：query、空=50、非空=searchHistory ∪ 标题；`HistoryScreen` 搜框 + 卡「删除」+ 确认。
4. MainActivity：`onDeleteTask` busy 门、join、refresh 快照。
5. spec：database / state-management / component / directory-structure / logging / quality。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :core:runtime:test :feature:history:testDebugUnitTest :app:testPlayDebugUnitTest
```

真机：空框仍 50 条；搜 UNO 找到旧失败/完成卡且无刘备咖啡；删默认里一条窗口还在；删「对话 2」最后一条回到默认；忙时不能删。

## Do not

- bump DB、改 Chat 检索、分词器。
- `task.py start` 未获规划摘要批准前改产品代码。
