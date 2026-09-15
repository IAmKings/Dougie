# Design: LLM 注入当前会话近期轮次

## Boundary

| 模块 | 职责 |
|------|------|
| `:core:runtime` | 从 `TaskStore.listByConversation` 取出**早于本轮**、`COMPLETED`、有非空 `finalAnswer` 的任务，交给拼装（Loop 里在 `retrieveMemories` 之后、第一次 `stream` 之前取一次即可） |
| `:core:llm` | 把这些轮变成 user/assistant 文本；滑动窗口；云端 `buildRequestJson`；端侧 `localPrompt` |

不改 UI、新对话、任务页。不新建 `ContextBuilder` 类型。不引入 tiktoken。

**依赖**：`09-15-chat-thread-default` 的 `conversationId` + `listByConversation`。

## Which turns

当前 `task.conversationId` 内，`taskId != current`，`status == COMPLETED`，`finalAnswer` 非空白。升序。**失败轮不进 prompt**（避免「任务失败：…」被当成助手）。

旧轮只取 `input` + `finalAnswer`。忽略 `toolTrace` / attachments / 图片。

## Budget

本地估算（根 `PRD.md` §8.3）：汉字 `ceil(n / 0.75)`，空白分隔英文词 `ceil(words / 0.25)`，其余字符按 1。只用于**历史轮**裁剪。

超限：从**更早**的历史轮丢掉整轮（一对 user+assistant）。不得丢掉：`systemPrefix`（含 Known facts:）、本轮 `task.input`、本轮 tool 消息。

云端封顶：最多 16 轮或历史合计约 4k 估算 token（先实现轮数+字符，单测锁「第 17 轮被丢」）。端侧：最多 4 轮、更严字符（建议历史合计 ≤ 800 字）；`localToolProtocolActive` 时最多 2 轮且历史里不得出现 `{` 工具 JSON。

不做 8K 硬达标证明。

## Cloud `messages`

```text
system          ← 现有 systemPrefix(task, descriptors)
user/assistant  ← 历史轮（无 tool_calls）
user            ← 本轮 userContent(task)
assistant+tool  ← 仅本轮 toolTrace（现有循环）
```

## Local `localPrompt`

在身份/协议块与 `userBlock` 之间插入：

```text
近期对话：
用户：…
助手：…
```

协议激活时仍只教 `localTeachable`；历史块不得含旧轮 `resultJson`。`stripLeadingQuestion` 不变。

## Wiring

`LoopEngine.collectLlmTurn` / `gateway.stream` 今日只传 `LoopContext(task)`。可选：

- A. `AgentTask` 增加瞬时 `priorTurns: List<Pair<String,String>>`（不进 codec / 不落库），Loop 填好再 stream；或
- B. `LlmProvider.stream` 增加可选 `priorTurns` 参数。

选 **A**：少改 Provider 接口；codec 编码时省略空 `priorTurns`（或根本不写进 JSON）。`copy()` 时带上，避免 THINKING emit 丢掉。**禁止**把 prior 写入 `snapshot_json`（体积+隐私）。

`ChatPromptAssembler` / `OpenAICompatibleProvider.buildRequestJson` / `ChatLlmProvider.promptFor` 读 `task.priorTurns`。

## Logging

禁止 log 历史原文、拼好的 prompt、finalAnswer。单测断言请求 JSON 含上一轮文本即可。

## Rollback

`priorTurns` 空 = 与启动刀结束时行为相同。新对话后 list 为空，第一轮无历史。
