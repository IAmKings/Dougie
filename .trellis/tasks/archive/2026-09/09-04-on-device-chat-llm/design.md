# Design: PJZ110 聊天 LLM 选型（LiteRT-LM）

## Outcome

research 笔记 + 可选 **sideload** LiteRT-LM 探针。产品聊天仍走云端。

## Why not GGUF JNI

自维护 llama.cpp 在 PJZ110 上 Vulkan 已翻车，CPU 意图路径也不达标。聊天不需要再养一套 ggml。改用 Google **LiteRT-LM** Kotlin API：`Engine(EngineConfig(modelPath, backend))` + `Conversation.sendMessage` / Flow；模型为 `.litertlm` 文件系统路径（可放 `filesDir`）。

底层仍有 native，由 AAR 提供，不在本仓库编 llama.cpp。

## LiteRT-LM vs ExecuTorch vs MNN（选型对比）

三者都是「框架跑生成式 LLM」，不是 MiniRBT 那种单次 encoder。意图路径继续 ONNX；这里只比**闲聊**。证据主要来自 `08-19-local-model-import/research/android-intent-runtime.md`（2026-08）。

| 维度 | LiteRT-LM（当前方案） | ExecuTorch | MNN-LLM |
|---|---|---|---|
| 谁维护 | Google AI Edge；MediaPipe LLM 已 maintenance | Meta / PyTorch | 阿里巴巴 |
| Android 接入 | Maven `litertlm-android`，Kotlin `Engine` + `Conversation` | Maven `executorch-android`（默认 XNNPACK）；完整 LLM 常跟官方 LlamaDemo / 自编 runner | 官方 Android Chat 示例；典型是 **CMake + JNI**，不是「加一个 AAR 就聊」 |
| 权重格式 | 现成 `.litertlm`（HF LiteRT Community，含 Qwen2.5/Qwen3 小模型） | 自己 `export_llama` → `.pte`；QNN 必须走 **另一套 Qualcomm 脚本**，不能 `--qnn` 乱导出 | `llmexport.py` → `llm.mnn` + weight + tokenizer + `config.json` 目录 |
| CPU | `Backend.CPU()` | **XNNPACK**（官方建议先走这条） | `backend_type: cpu` |
| GPU | **OpenCL**（`libOpenCL.so`），不是 Vulkan | **Vulkan** | **OpenCL**（`MNN_OPENCL`） |
| NPU / 高通 | 统一 NPU + Play **PODAI**（本切片不测） | **QNN** 一等公民，但要 QNN SDK、芯片 lowering；macOS 主机编不了 QNN | 文档写明 **Hexagon** 跑 Qwen3-0.6B（4bit 对称 + Transformer C4）；另有 QNN plugin 路径 |
| 和 Dougie 的冲突 | 独立栈，不碰 sherpa 的 `libonnxruntime.so` | 独立栈；QNN `.so` 体积/许可另算 | 独立栈；要带 `libMNN*.so` / HTP skel |
| PJZ110 风险 | GPU 避开已失败的 Vulkan；NPU 包装最「官方」但 PODAI 产品化重 | GPU 与 llama.cpp **同一类 Vulkan**；CPU/QNN 才是合理路径 | GPU/Hexagon 更贴 Adreno/HTP；OpenCL 在部分骁龙上有「第一次 tuning 很慢」的社区报告 |
| 公开速度（他机） | Qwen3-0.6B：Vivo X300 Pro CPU decode ~9 tok/s，GPU ~21 tok/s | 论文/artifact 在 S25 Ultra 上有 XNNPACK/Vulkan/QNN 表，需本机复测 | 无与 LiteRT 同机对照；Hexagon 才是冲速度的理由 |
| Spike 成本 | **最低**：下权重 + Maven + sideload | 中：要导出 `.pte`、tokenizer lookahead、后端 AAR 组合 | 中高：NDK 编译 LLM、多文件模型目录、Hexagon 还要 skel/`ADSP_LIBRARY_PATH` |

**对本切片的含义**

- 要验证「常规 SDK 能否在 PJZ110 上稳定出中文」，LiteRT-LM 工程最短，GPU 也不踩 Vulkan。
- 要验证「这台 8 Elite 能不能跑得快」，MNN Hexagon 或 ExecuTorch QNN 更对口，但都不是「加依赖就能测」，且 QNN/Hexagon 已划出本切片。
- ExecuTorch 的默认 GPU（Vulkan）与仓库里已失败的 llama 路径同类，**不宜作为第一枪 GPU**。

## Trial order

1. LiteRT-LM **CPU** + 小 Qwen `.litertlm`（手动拷到 `filesDir/models/chat/`）。
2. 若 CPU 过慢但进程稳定：同模型 **GPU（OpenCL）**。Manifest 按官方补 `uses-native-library`（`libOpenCL.so`），仅 sideload。
3. 停。NPU / ExecuTorch / MNN 只写 research，本切片不接。

不把 sherpa 的 ORT 拿来跑 decoder。

## Probe

- 仅 sideload Debug：加载本地 `.litertlm`，屏幕显示 load/first-token/tok/s。
- Play 不含 LiteRT-LM 聊天权重；`checkChannelLeak` 继续拒 `.gguf`，并确认 APK 未打进 `.litertlm`。

## Compatibility

- `localLlmReady` 保持 false。
- MiniRBT / `dougie_intent` 不动。
- 权重 gitignore。
