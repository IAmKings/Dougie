# 端侧对话 LLM（真机选型 spike）

## Goal

在 **PJZ110** 上用**常规 Android 端侧推理 SDK**（不自维护 llama.cpp JNI、不跑 GGUF）选出能稳定生成中文闲聊的路径，写入 `research/`。不接 Loop、不改设置下载、不点亮 `localLlmReady`。

## Background

- Q1：先选型。Q2：放弃自研/复活 **JNI + GGUF**；改走框架 AAR。
- llama.cpp Vulkan 在该机已失败；`LlamaJni` 已删除。意图保持 MiniRBT ONNX + 现有 `libonnxruntime.so`。
- 「常规」仍会带厂商 `.so`（在 AAR 里），只是不自己编 ggml / 不吃 `.gguf`。
- 2026-08 调研：MediaPipe LLM 已 maintenance；Google 主路径是 **LiteRT-LM**（`.litertlm`，`Backend.CPU/GPU/NPU`，GPU 走 OpenCL 而非 Vulkan）。ExecuTorch / MNN 为备选，本切片不并行接入。

## Requirements

- R1 主试 **LiteRT-LM**（Maven `litertlm-android`）+ Hugging Face LiteRT Community 的中文可用小模型（优先 Qwen2.5-0.5B / 0.6B 档 `.litertlm`）。先 **CPU**，再视情况 **GPU（OpenCL）**。不测 NPU/Play PODAI。
- R2 不恢复 llama.cpp、不引入 `.gguf`、不把聊天接到意图 JNI。不第二份 ORT GenAI（避免和 sherpa 的 `libonnxruntime.so` 撞）。
- R3 不接 `LoopEngine` / catalog 聊天行 / `localLlmReady=true`。
- R4 不把 prompt、生成全文、权重路径打进 Logcat。数字进 research。
- R5 Play `checkChannelLeak` 无 GGUF / 无正式聊天权重。探针权重只放 `filesDir` 或 gitignore。
- R6 不可用则 research 写明，下一候选 **ExecuTorch XNNPACK** 或 **MNN OpenCL**（不在本切片实现）。

## Acceptance Criteria

- [x] AC1 `research/pjz110-chat-llm.md`：机型、LiteRT-LM 0.16.1、CPU+GPU 度量、结论采用 LiteRT-LM。
- [x] AC2 真机 CPU/GPU 均成功出中文（CPU 首 token 2453ms / 生成 3810ms；GPU 799ms / 964ms）。
- [x] AC3 `:app:checkChannelLeak` 过；意图短路径未改。

## Out of scope

- llama.cpp / 自维护 GGUF JNI。
- 设置下载、无云端可聊、本地 tool-calling。
- QNN/GenieX 独立 SDK、向量记忆、两套生成引擎并行。
