# Design: 侧载本地 Chat LLM

## Outcome

侧载 Chat 在策略上无云端时走 LiteRT-LM；有云端配置时仍走现有 OpenAI 兼容 provider。

## Layout

- JVM：`ChatModelLayout`（`models/chat/` + 约定 `.litertlm` 文件名）+ catalog offer `id=chat`。`OfficialModelCatalog.isInstalled` 认识该 id。
- `:core:llm`：`SelectingLlmProvider`（或同等）在 `stream`/`generate` 时按 R3 委托；`isLocal` 反映**即将使用的**后端（gateway 才能放行本地）。
- `:tool:chatllm`：把现有 Engine 做成 `LlmProvider`（Java/Kotlin 与 0.16.1 AAR 约束不变：stubs + `runtimeOnly`）。进程内单例 Engine，先 `Backend.GPU()`，失败再 CPU。
- `:app` 侧载注入本地 provider；Play 不注入。`MainActivity.localLlmReady` 读 `ChatModelLayout.isPresent`，不读意图。
- 设置：`AppOfflineModels.offers` 增加聊天行。探针 Activity 可留作 sideload Debug，不是产品入口。

权重 URL 用 HF resolve（与 ASR 相同模式）；SHA-256 实现时对首选文件实测写入 catalog / BuildConfig。

## Loop

本地模型仍收到现有 tool descriptors。0.6B 可能乱调工具：不为此切片改 sanitizer；失败走现有 `TOOL_FAILED` / 未知工具文案。不接 NPU。

## Compatibility

- Play classpath 仍不得出现 `:tool:chatllm` / `litertlm`。
- `checkChannelLeak` 保持拒 APK 内 `.litertlm`。
- 不恢复 llama.cpp。
