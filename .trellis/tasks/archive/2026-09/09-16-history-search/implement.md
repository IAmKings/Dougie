# Implement: Chat 检索历史对话

## Order

1. `:core:model` `ConversationHit` + `AgentTask.retrievedConversationHits`（默认 empty）。`AgentTaskTest` 确认 `copy` 保留。
2. `TaskSnapshotCodec` 编解码；旧 JSON 无字段 → empty。`TaskStoreTest` 或 codec 单测。
3. `TaskStore.searchCompletedTurns`（InMemory + Sqlite）；needles / exclude / 不匹配 toolTrace。`TaskStoreTest`。
4. `LoopEngine`：`attachPriorTurns` 后检索 + 预算 + 标题；记忆关闭仍搜；短路径不搜。`LoopEngineTest`。
5. `ChatPromptAssembler.systemPrefix` 「相关历史对话」；`ChatPromptAssemblerTest` + 云端 `OpenAICompatibleProviderTest` 断言 system 含摘录、不含 tool 参数。
6. `citationSources` 合并 `sourceLabel`。`ChatUiStateTest`。Debug 不映射正文。
7. `DougieApplication`：LoopEngine 能读 `ConversationTitles`（或 `() -> Map`）。
8. spec：`database-guidelines.md`、`logging-guidelines.md`、`directory-structure.md`、`state-management.md`、`type-safety.md`。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :core:model:test :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :feature:debug:testDebugUnitTest
```

真机：窗口 A 聊 UNO 并完成；「新对话」后在窗口 B 问关键点，回答应能用到 A 的内容，气泡下来源不是 UUID；问「现在几点」仍走短路径/工具，不得整句「未找到」。

## Do not

- bump DB、任务页搜框、强制「未找到」、记忆库写入历史命中。
- `task.py start` 未获规划摘要批准前改产品代码。
