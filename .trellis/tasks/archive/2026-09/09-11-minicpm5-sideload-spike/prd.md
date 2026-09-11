# 侧载 MiniCPM5 LiteRT 真机 spike

## Goal

在 PJZ110 侧载上，用现有 LiteRT-LM 探针验证 MiniCPM5 INT4 `.litertlm` 能否稳定生成一句中文闲聊，把度量写入本任务 `research/`。先测 2B；仅当 2B 在 CPU 与 GPU 上都完不成时再测 1B。不改设置下载、Loop、或现网 Qwen3-0.6B 布局。

## Background

- 2024 [MiniCPM-2B-sft-bf16](https://huggingface.co/openbmb/MiniCPM-2B-sft-bf16) 的 GPTQ/MLC INT4 不能进 `ChatLlmProvider`。笔记：`.trellis/workspace/kuluoluo/research-minicpm-2b-sideload.md`。
- 现网：`ChatModelLayout.MODEL_FILE` = `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`（~328MB），LiteRT-LM 0.16.1，`ThinkingConfig(false)`。
- 探针：`ChatLlmSpikeActivity` / `ChatLlmProbe.findModel` 在 `filesDir/models/chat` 或外部 `Android/data/…sideload/files/models/chat` 里按文件名排序取**第一个** `.litertlm`。
- 对口权重（Apache-2.0）：[litert-community/MiniCPM5-2B](https://huggingface.co/litert-community/MiniCPM5-2B) `MiniCPM5-2B_int4.litertlm`（1.55GB）；失败后备 [MiniCPM5-1B](https://huggingface.co/litert-community/MiniCPM5-1B) `minicpm_wi4b32_wi8_afp32.litertlm`（793MB）。
- 同类切片：`09-04-on-device-chat-llm`。

## Requirements

- R1 权重只从 Hugging Face HTTPS 拉取 `.litertlm`，放设备外部/内部 `models/chat`，不入库 git。目录里同一时刻只放当前候选文件（避免探针按字典序误选 Qwen 或上一档 MiniCPM）。
- R2 沿用 `ChatLlmSpikeActivity` / `ChatLlmProbe`。固定 prompt「用一句话介绍你自己」、`ThinkingConfig(false)`。每档先 CPU 再 GPU OpenCL。不测 NPU。仅当超时/空输出阻碍结论时，才允许最小改动探针超时或 max tokens。
- R3 先测 2B INT4。用户 2026-09-11 要求即使 2B 已通也补测 1B；1B 用 **GPU 优化包** `minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm`（现网本地是 GPU 优先）。仍点一次 CPU，只作降级对照。
- R4 不恢复 llama.cpp / GGUF / MLC；不接 2024 MiniCPM-2B-sft GPTQ。
- R5 不 Logcat prompt、补全、权重路径。屏幕可显示文件名与数字。
- R6 不改 `OfficialModelCatalog`、`ChatModelLayout.MODEL_FILE`、设置「对话」行、`LoopEngine`、`localLlmReady` 语义。测完可把 Qwen 文件放回产品目录。
- R7 Play `checkChannelLeak` 仍过；APK 不打进 `.litertlm`。

## Acceptance Criteria

- [x] AC1 `research/pjz110-minicpm5.md`：机型、文件名、体积、CPU/GPU load / 首 token / 生成 / 字符每秒或失败类名；2B 若降级则写明原因与 1B 对照。
- [x] AC2 结论写清：2B 与 1B gpu_opt 均可后续 catalog；速度/体积优先 1B，能力优先 2B。现网仍建议暂留 0.6B 直到 catalog 切片。
- [x] AC3 产品聊天包文件名与 catalog SHA 未改；Play `checkChannelLeak` 过。

## Out of scope

- 设置下载切换、多模型选择器。
- MiniCPM5 XML 工具解析；本切片不测工具调用。
- Play LiteRT、NPU/PODAI、同轮云失败改本地。
- 把探针做成设置入口。
