# Design: LiteRT → Loop ToolCall

## Wiring

`DougieApplication` 把 ` { tools.values.map { it.descriptor } } ` 同时交给 `OpenAICompatibleProvider` 与 `ChatLlmProvider`（扩展 `ChannelHooks.localChatProvider` / `get` 参数）。Play 仍返回 null。

## Prompt

`ChatPromptAssembler.systemPrefix(task, descriptors)`：在人设之后追加「可用工具」段（name + 短 description）。人设 `IDENTITY` 不改、仍不含工具名。

本地 `localPrompt` 继续拼用户句和已有 `toolTrace` 结果，供第二轮带工具结果回去。

## Emission

MVP：**文本协议 + JVM 解析器**（放 `:core:llm`，不依赖 Android）：

模型若要调用工具，整段回复必须是一行 JSON，例如 `{"name":"time","args":{}}`（字段名以实现为准，测例钉死）。解析成功 → 只发 `LlmEvent.ToolCall`，不把该 JSON 当 `TextDelta`。否则整段当文本。

实现时对照 0.16.1 AAR：若 `Conversation`/`extraContext` 有官方 function calling，适配进同一 `ToolCall`；stub 不够就扩 Java 17 stub，**runtimeOnly AAR 仍仅 sideload**。

`ToolCallSanitizer` 仍是未知名 / 坏参数的闸门。

## Tests

- 解析器：合法 time、非法 JSON、夹杂中文、未知 name。
- Assembler：有 descriptors 时出现 `time`，`IDENTITY` 仍不含 `clipboard_read`。
- Loop：假本地 provider 先 ToolCall `time` 再文本 → `LOCAL_LLM`（沿用现 `SpyLocalLlm` 模式或新 spy）。

真机 AC1 不在 CI。
