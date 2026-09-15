# Implement: LLM 注入当前会话近期轮次

## Depends on

先归档 `09-15-chat-thread-default`。未完成不要 start。

## Order

1. `:core:model`：`AgentTask.priorTurns`（默认 empty；**不**进 `TaskSnapshotCodec`）。
2. `:core:llm`：历史裁剪 + 估算；`buildRequestJson` 插入 user/assistant；`localPrompt` 插入「近期对话」。单测：两轮指代；新会话无历史；本轮 tool 仍在历史之后；端侧协议 prompt 无旧 JSON；超 16 轮丢最早。
3. `:core:runtime`：`LoopEngine` 在第一次 LLM 前 `listByConversation` 填 `priorTurns`（无 store 则空）。`LoopEngineTest` 用 InMemory store 两轮任务。
4. 不改 Chat UI。spec：`directory-structure.md` 拼装节补 Recent Conversation；logging 仍禁 prompt。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :core:llm:test :core:runtime:test :core:model:test
```

JDK 17。真机（云端）：同一会话先报一个名字，再问「他叫什么」，抓到的请求 body 含第一轮。新对话后第一发不含。

## Risk / rollback

- 把 `priorTurns` 写入 snapshot → 任务库膨胀且含对话。codec 必须省略。
- 端侧塞满历史 → 0.6B 乱出 JSON。协议轮最多 2 轮且无 `{`。
- 失败轮当 assistant → 模型学错误文案。只注入 COMPLETED+非空终答。

## Do not

- `ContextBuilder` 接口、tiktoken、US-001 FTS、重放旧 `toolTrace`。
- 改 MemoryGate / `retrieveMemories` 条数。
