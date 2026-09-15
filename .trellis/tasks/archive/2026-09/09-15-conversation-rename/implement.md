# Implement: 会话可重命名

## Order

1. `conversationDisplayName` + `toHistorySections` 接受 titles；扩展 `HistoryItemTest`。
2. `ConversationTitles` + `PreferenceStore` JSON 键 + `StateFlow`；`save()` 不碰该键。
3. History：「改名」对话框 → `setTitle`；refresh 显示。
4. Chat 顶栏一行；MainActivity 传入当前显示名。
5. spec：`state-management.md`（titles prefs、Chat 一行、History 改名）；`directory-structure.md`；`database-guidelines.md`（仍无 conversations 表）；`hook-guidelines.md`（**保存配置**不丢 titles）。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:history:testDebugUnitTest :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest
```

JDK 17。真机：改名、清空回退、新对话「新对话」、保存配置后仍在。

## Do not

- `conversations` 表、SQL 列、DB bump、标题进 snapshot / LLM。
- Chat 顶栏改名、侧栏、删除。
- log 自定义名。
