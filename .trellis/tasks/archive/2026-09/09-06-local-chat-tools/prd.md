# 本地 LiteRT 接入 Loop 工具

## Goal

侧载本地对话能发出 `LlmEvent.ToolCall`，与远程共用同一份 `ToolDescriptor` 和 Loop 执行。验收以「现在几点了」走 `time` 为准。

## Depends on

`09-06-chat-prompt-parity` 已合入（`ChatPromptAssembler`）。

## Background

- `LoopEngine.collectLlmTurn` 已执行 `ToolCall`。远程用 OpenAI `tools` JSON。
- LiteRT 0.16.1 stub 的 `Conversation.sendMessageAsync` 只有文本；本刀用可测的文本协议解析成 `LlmEvent.ToolCall`。实现时若 AAR 有官方工具 API，仍须落到同一事件，且不把 AAR 打进 Play。
- 有对话 LLM 时 MiniRBT 已跳过。

## Requirements

- R1 本地 `stream` 能产出 `LlmEvent.ToolCall`，随后 `executeToolPass`（Policy / 确认卡 / 中文失败文案不变）。
- R2 `ChatLlmProvider` 注入与远程同一 `toolDescriptors`（`DougieApplication.tools` 全表）。AC 不要求 0.6B 打中每一个工具。
- R3 接通后 `ChatPromptAssembler` 增加工具清单块（人设仍无工具名；清单单独一段）。远程 system 也走该块，工具 JSON 仍保留。
- R4 解析失败则当普通文本；未知工具仍由 `ToolCallSanitizer` 拒绝。Cancel 不映射为 `LLM_FAILED`。
- R5 不 Logcat 提示/补全/路径。Play 无 `:tool:chatllm`。

## Out of scope

- Play LiteRT、NPU、改人设原文、本地视觉、把 MiniRBT 挡回 LLM 前、保证 0.6B 会调日历/截屏。

## Acceptance Criteria

- [ ] AC1 侧载关出境 + 对话包：问「现在几点了」，`completionPath` 为 `LOCAL_LLM`，工具迹含成功 `time`，不是 `LOCAL_INTENT`。
- [ ] AC2 JVM：解析缝把约定工具 JSON 变成 `LlmEvent.ToolCall`；假 provider 能驱动 Loop 调 `time`。拼装含工具清单且人设行仍无 `clipboard_read`。
- [ ] AC3 `:app:checkChannelLeak` 过。
