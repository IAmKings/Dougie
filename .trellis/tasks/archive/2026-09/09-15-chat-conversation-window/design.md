# Design: 对话窗口（parent）

## Boundary

本 parent **不写产品代码**。实现只发生在子任务。

| 子任务 | 交付 |
|--------|------|
| `09-15-chat-thread-default` | `conversationId`、默认会话 `"default"`、窗口多轮、「新对话」、任务页点卡片打开**整段**会话 |
| `09-15-chat-thread-llm-context` | 当前会话已完成轮的 user/assistant 滑进云端/端侧 prompt |

依赖写在子任务 `prd.md` / `implement.md`。不要 `task.py start` 本目录。

## Shared contracts (both children)

- 一轮用户请求仍是一个 `AgentTask`。禁止把多轮塞进同一个任务。
- 会话身份 = `AgentTask.conversationId`。默认值常量 `ConversationIds.DEFAULT = "default"`。旧快照缺字段解码为该常量。
- 当前打开的会话 = prefs 独立 key，不是 `conversations` 表。
- 重返只走「任务」扁平列表；对话页无最近会话。
- `:core:*` 继续 JVM 纯净。`:feature:chat` 不直接打开 SQLite，只吃 `TaskManager` / `TaskStore` 接口上的 `AgentTask`。

## Deliberate gaps vs root `PRD.md`

见 `prd.md` Alignment。设计层同样禁止：四张会话表、`ContextBuilder` 接口、tiktoken、US-001 消息 FTS。

## Integration review (after both children)

- 同一会话两轮：窗口有四条气泡，第二轮云端 `messages` 含第一轮文本。
- 新对话后第一轮 prompt 无上一会话。
- 任务页点旧会话任一条：对话页是整段，LLM 下一发跟新指针。
