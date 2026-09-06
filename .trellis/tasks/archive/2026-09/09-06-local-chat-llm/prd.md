# 端侧对话接入 Chat

## Goal

侧载在已装 LiteRT-LM 聊天包、且云端策略不可用时，Chat 能本机闲聊（不出境），头像为「本地」。意图仍是 MiniRBT。不接 NPU，不做同轮云端失败重试。

## Background

- PJZ110 已验证 LiteRT-LM 0.16.1（CPU / OpenCL GPU）。`:tool:chatllm` 仅 sideload。Play 泄漏检查禁止 `litertlm` / `.litertlm`。
- Chat 现仅 `OpenAICompatibleProvider`（`isLocal=false`）。`EgressGateway` 在调用前检查 `allowCloud` / key。`intelligenceMark`：云端配置可用为 Super；拦截/缺 key 且本地就绪为本地；网络/LLM 失败即使本地就绪也是不可用。
- 设置离线行只有 ASR / TTS / 意图。

## Requirements

- R1 设置增加聊天模型行：`.litertlm`（首选 `Qwen3-0.6B_dynamic_wi4b32_afp32`，Hugging Face LiteRT Community），确认后下载到 `filesDir/models/chat/`；已安装可测，不把全文打进 Logcat。
- R2 侧载提供 `isLocal=true` 的流式 `LlmProvider`，接到同一 `LoopEngine`。`localLlmReady` 仅当该聊天文件就绪。
- R3 提交时选路：`allowCloud && key 非空` → 云端；否则若本地就绪 → 本地；否则仍走云端 provider 以便现有拦截文案。同一句云端 `NETWORK_FAILED` / `LLM_FAILED` / `LLM_TIMEOUT` 不自动换本地重跑。
- R4 关出境或清 key 后的下一句，在包就绪时走本地。
- R5 Play 无 LiteRT AAR、无 `.litertlm`、无 `ChatLlmSpikeActivity` 泄漏。
- R6 引擎进程内常驻；先 GPU，失败则 CPU。
- R7 MiniRBT 意图短路径不变。不把意图包当成聊天模型。
- R8 不 Logcat prompt、补全、权重路径。

## Acceptance Criteria

- [ ] AC1 侧载未装聊天包：与现在一致（无云端则失败/拦截）。
- [ ] AC2 侧载已装包 + 未授权或无 key：一轮中文闲聊可完成本地生成；`isLocal` 使 gateway 放行。
- [ ] AC3 已授权且有 key：即使本地包在，仍走云端。
- [ ] AC4 `intelligenceMark`：仅聊天包就绪可 LOCAL；意图包不得点亮。
- [ ] AC5 Play `:app:checkChannelLeak` 过。
- [ ] AC6 JVM：选路、egress 本地放行、catalog `isInstalled`、头像映射。

## Out of scope

- NPU / PODAI、Play 打进 LiteRT、同轮云端失败 failover、向量记忆、把探针做成设置入口。
