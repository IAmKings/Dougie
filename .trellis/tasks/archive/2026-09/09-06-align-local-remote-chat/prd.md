# 对齐本地与远程对话的身份和工具

## Goal

远程与本地走同一套身份、工具链和任务上下文；切换出境只改变推理能力，不改变 Dougie 是谁、能用哪些本地工具、是否带上记忆和附件说明。

## User value

关出境后仍是同一个助手。少「本地不知道自己是谁 / 调不了时间」这类接线断裂。

## Task map

| 子任务 | 交付 | 依赖 |
|--------|------|------|
| `09-06-chat-prompt-parity` | 共用身份与任务上下文（记忆、附件说明、中文）；身份段不含工具名；本地仍不发 `ToolCall` | 无 |
| `09-06-local-chat-tools` | LiteRT 发出 `LlmEvent.ToolCall`，走现有 Loop / Policy / 确认卡 | 上一刀的共用 prompt 源 |

父任务只做集成验收，不直接改代码。已采纳：拆两刀；身份不含工具名；人设中文唯一原文；本地注入全套 `toolDescriptors`，本刀子任务 AC 只强制 `time`。

## Integration acceptance

- [ ] I1 开/关出境问「你是谁 / 你是什么模型」：都是 Dougie、中文；本地表明本机离线，远程不否认 local-first。
- [ ] I2 关出境后「现在几点了」走本地工具（时间），不是胡编时钟、也不是 MiniRBT（有对话 LLM 时已跳过意图）。
- [ ] I3 Play 无 LiteRT 泄漏；SCREEN 像素仍不出境。

## Out of scope

- NPU、Play LiteRT、同轮云失败改跑本地、本地视觉吃相册 JPEG、把 MiniRBT 重新挡在 LLM 前。
