# LLM 优先工具，意图仅无模型时

## Goal

只要远程或本地对话模型能接这一轮，就不跑 MiniRBT 短路径，闲聊（含「你是什么模型」）不得被收成读剪贴板。两边都不可用时，意图短路径照旧。本任务不给 LiteRT 加工具调用。

## User value

开云端与关云端对闲聊一致：先对话模型，再 Loop 工具（远程已具备；本地口答，工具另开任务）。无对话能力时，时间/电量等仍可本地模板。

## Background

- MiniRBT 在 `LoopEngine.completeFromIntentIfMatched` 先于 LLM。`clipboard_read` 的 `parseShortcutArgs` 恒为 `{}`，置信度 ≥ 0.5 即执行。
- 现 `skipIntentShortcut = { provider.isLocal }`：只在关出境且对话包就绪时跳过。云端可用时「你是什么模型」仍会被意图抢走。
- 远程 `OpenAICompatibleProvider` 已带 `ToolDescriptor`；`collectLlmTurn` 已执行 `LlmEvent.ToolCall`。侧载 `ChatLlmProvider` 只有 `TextDelta`。
- 意图截屏成功靠 `CompletionPath.LOCAL_INTENT` 钉作曲区；LLM 环截屏不钉。有 LLM 后「截个屏」不再走短路径，钉图不在本任务补。

## Requirements

- R1 远程已配置（`allowCloud && key`）或本地对话包就绪：本轮不 `classify`、不跑 MiniRBT。开发者页不得为「本地意图」。
- R2 「你是什么模型」「你是本地模型吗」不得出现剪贴板正文。
- R3 远程未配置且本地对话未就绪、意图包就绪：「现在几点了」仍走现有短路径与中文模板。
- R4 不把 `llm.isLocal` 写进 `LoopEngine` 作为跳过条件；继续只用 `skipIntentShortcut`。App 改为「有对话 LLM」时跳过（云端配置 **或** 本地包就绪）。
- R5 不改 LiteRT、不改 Play 泄漏面、不 Logcat prompt。

## Acceptance Criteria

- [ ] AC1 云端可用 + MiniRBT 会把句子标成 `clipboard_read`：仍走 LLM，`classifyCount == 0`，无 `clipboard_read` 工具迹，`completionPath` 为 `REMOTE_LLM`（假若 provider `isLocal=false`）。
- [ ] AC2 关出境 + 对话包就绪：同上，路径为 `LOCAL_LLM`（现有 skip 测例保留并随谓词更新）。
- [ ] AC3 关出境 + 无对话包 + 意图就绪：「现在几点了」仍 `LOCAL_INTENT`，不调 LLM。
- [ ] AC4 JVM：`LoopEngineTest` 覆盖 AC1–AC3；不跑真机云。`:app:checkChannelLeak` 若未改 flavor/AAR 可不变，改 `DougieApplication` 接线后仍应通过。

## Out of scope

- 侧载 LiteRT function calling / `LlmEvent.ToolCall`（下一步任务）。
- 提高 MiniRBT 阈值、给 `clipboard_read` 加槽位、NPU、Play LiteRT、同轮云端失败改跑本地。
- LLM 环 `screen_capture` 钉作曲区；有 LLM 时意图截屏短路径一并取消。
