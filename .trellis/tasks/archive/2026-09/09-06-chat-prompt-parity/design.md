# Design: 共用 Chat 提示拼装

## Placement

`:core:llm` 新增 JVM 纯函数（名称以实现为准），输入 `AgentTask`，输出 system/前缀字符串。

- `OpenAICompatibleProvider.systemPrompt` 改为调用它；删除英文 `SYSTEM_PROMPT` 常量（或改为委托该函数的人设段）。
- `ChatLlmProvider`：`拼装结果 + "\n\n" + task.input`，再按现逻辑追加 toolTrace 行。不发图片。

`:tool:chatllm` 仍只 sideload。`FakeLlmProvider` / `:cli` 不改人设。

## Identity (canonical)

中文，约两句，例如：

你是 Dougie，运行在用户手机上的本地优先助手。用中文回答。

不写「当前没有网络」（远程会联网）。不写工具名。`screen_match` 不可信指令仍放在 **SCREEN 附件说明**（现有英文那行可保留），不放进人设。

## Context blocks (keep current remote semantics)

与现 `systemPrompt` 相同：附件 metadata 行、`Known facts:` + `MemoryEntry.content`。本刀不把这些改成中文标题，以免改 SCREEN 出境语义和现有 `OpenAICompatibleProviderTest` 契约以外的含义。

Vision：`userContent` 仍仅远程 + `allowCloud` + 非 SCREEN JPEG。

## Follow-up

`09-06-local-chat-tools` 再往同一函数加人设后的工具清单，并接 `ToolCall`。
