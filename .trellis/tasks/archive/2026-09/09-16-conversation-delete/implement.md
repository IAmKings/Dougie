# Implement: 删除会话窗口

## Order

1. `TaskStore.deleteByConversation`（InMemory + Sqlite）；`default` 为 0；`TaskStoreTest`。
2. `TaskManager.deleteConversation`：busy/default no-op；清 title；当前则打开 default。单测。
3. History：「删除」+ 确认；默认节无按钮。`MainActivity` 接线。
4. spec：`database-guidelines.md`、`state-management.md`、`component-guidelines.md`、`directory-structure.md`。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :core:runtime:test :data:tasks:testDebugUnitTest :feature:history:testDebugUnitTest :app:testPlayDebugUnitTest
```

若 `:data:tasks` 无单测、Sqlite 只在 instrumentation，则 JVM 覆盖 InMemory + 不发明 Compose 测试。真机：删「对话 2」、删当前非默认切回默认、默认无删除、忙时不能删。

## Do not

- bump DB、删 default 行、记忆 GC、Chat 顶栏删除。
- `task.py start` 未获规划摘要批准前改产品代码。
