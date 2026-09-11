# MiniCPM 2B / INT4 能否接入 Dougie 侧载

- Date: 2026-09-10
- Question: 侧载能否使用 [openbmb/MiniCPM-2B-sft-bf16](https://huggingface.co/openbmb/MiniCPM-2B-sft-bf16) 及其手机 INT4。
- Verdict: **该 2024 MiniCPM-2B 不能直接进现网侧载。** 若目标是「2B 级端侧 INT4」，应评估 **MiniCPM5-2B 的 `.litertlm`**，而不是这份 GPTQ / MLC 发行物。

## Dougie 侧载现网约束

Sideload chat 只吃 LiteRT-LM 单文件 `.litertlm`：

- Engine：`com.google.ai.edge.litertlm:litertlm-android:0.16.1`，仅 `:tool:chatllm`，Play 禁止 `litertlm`。
- 布局：`filesDir/models/chat/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`（约 328MB）。
- 后端：进程内常驻，先 GPU（OpenCL）再 CPU。NPU / llama.cpp / GGUF 明确不恢复。
- 工具：`ChatPromptAssembler.localPrompt` 教一小份工具；整段回复必须是 `{ "name", "args" }` JSON，才进 `LocalToolCallParser`。
- 思考：`ThinkingConfig(false)`。

PJZ110（8 Elite）上现网 0.6B INT4：GPU 首 token ~0.8s，约 26 字符/秒；CPU 约 9 字符/秒。见 `.trellis/tasks/archive/2026-09/09-04-on-device-chat-llm/research/pjz110-chat-llm.md`。

## 用户链接的模型是什么

[MiniCPM-2B-sft-bf16](https://huggingface.co/openbmb/MiniCPM-2B-sft-bf16) 是 2024 面壁 **MiniCPM-2B**（非词嵌入约 2.4B，总约 2.7B）：

- 架构 `MiniCPMForCausalLM`，`trust_remote_code`，`scale_emb=12`，`dim_model_base=256`，词表 **122753**，ctx **4096**。
- 对话模板是 `<User>…<AI>`，不是 ChatML。
- 权重协议是 **GML**（学术免费；商用需 `cpm@modelbest.cn` 书面授权）。仓库代码 Apache-2.0，**权重不是 Apache**。

官方所谓「手机 INT4」有两条，**都不是 `.litertlm`**：

| 发行 | 实际格式 | 体积 | 运行时 |
|---|---|---|---|
| [openbmb/MiniCPM-2B-sft-int4](https://huggingface.co/openbmb/MiniCPM-2B-sft-int4) | AutoGPTQ `model.safetensors`（`quant_method: gptq`，4bit group 32） | **2.55 GB** | PC `transformers` + AutoGPTQ |
| OpenBMB Android demo | MLC-LLM `q4f16_1` + 编译出的 `libtvm` | 约 2GB 量级 | [MLC-LLM](https://github.com/mlc-ai/mlc-llm) / [mlc-MiniCPM](https://github.com/OpenBMB/mlc-MiniCPM) |
| 第三方 GGUF | llama.cpp | Q4_K_M ~1.8GB | 已从 Dougie 删除的路径 |

INT4 卡 README 为空；`config.json` 写明 GPTQ。卡片宣传的「流式略快于语速」对应 **MLC 实测**（8 Gen2 约 6.4–6.5 tok/s，8 Gen1 约 3.7，滑动窗口常压到 768 以省内存），不是 LiteRT。

Hugging Face LiteRT Community **没有** `MiniCPM-2B-sft` 的 `.litertlm`。

## 为什么现网侧载接不上这份 2B

1. **格式**：`ChatLlmProvider` / `ChatModelLayout` 只认一个 `.litertlm` 文件名。GPTQ safetensors 与 MLC 权重目录都打不开。
2. **架构**：LiteRT-LM 转换管线面向标准 Llama / Qwen 等；2024 MiniCPM 的 `scale_emb` / `scale_depth` 需要自定义建模。OpenBMB 当时走 MLC `MODEL_TYPE=minicpm`，不是 Google converter。
3. **第二引擎**：接入 MLC 等于再养一套 TVM JNI + OpenCL，Play 泄漏面、APK、下载与现网 LiteRT 并行。产品已否决再养 ggml。
4. **llama.cpp**：PJZ110 Vulkan 已失败；规格禁止把 `*.gguf` / `third_party/llama.cpp` 当聊天路径。
5. **工具协议**：本地解析器只要整段 JSON。MiniCPM-2B 无官方 function-calling；提示词还对不上 ChatML。
6. **体积与内存**：现网聊天 328MB。GPTQ 2.55GB；MLC 运行时还要 KV。8 Elite 能跑，但下载、`filesDir`、与 ASR/TTS 并存都比 0.6B 重一个数量级。
7. **许可证**：侧载若分发或内置该权重，商用要面壁授权。现网 Qwen3-0.6B LiteRT Community 卡是另一套条款。

**结论：不要把 `MiniCPM-2B-sft-bf16` / `-int4` 当作设置里可下载的聊天包。**

## 若目标是「能用的 2B 手机 INT4」：MiniCPM5

2025–2026 的 [openbmb/MiniCPM5-2B](https://huggingface.co/openbmb/MiniCPM5-2B) 才是接 Dougie 引擎的候选：

- 标准 `LlamaForCausalLM`，Apache-2.0。
- 官方列出 LiteRT-LM cookbook；Community 卡：[litert-community/MiniCPM5-2B](https://huggingface.co/litert-community/MiniCPM5-2B)。
- 手机文件：`MiniCPM5-2B_int4.litertlm` **1.55 GB**（blockwise-32 INT4 + INT8 embedding）。INT8 包 2.60 GB，偏桌面/Android 大内存。
- 要求 **litert-lm ≥ 0.16**（thought channel + `ThinkingConfig`）。Dougie 已 pin **0.16.1**，API 对得上。
- 包内 ChatML 模板 + 原生 tool XML；KV 预算在转换说明里是 **4096**（不是宣传的 131072）。
- Galaxy S26（SM8850 / Adreno，同类旗舰）litert-lm 0.16 实测（卡上数字）：

  | 文件 | 后端 | Decode | TTFT | Init | Peak RSS |
  |---|---|---|---|---|---|
  | int4 | GPU OpenCL | 16.1–18.6 tok/s | 0.56s | 11–13s | 1.14 GB |
  | int4 | CPU | 15.6–15.8 tok/s | 2.9–5.3s | 3–6s | 2.12 GB |

- INT4 **必须关 thinking**：开 think 时链会拖到 token 预算、答案常不落到可见通道。Dougie 已 `ThinkingConfig(false)`，方向正确。
- 更小兄弟：[litert-community/MiniCPM5-1B](https://huggingface.co/litert-community/MiniCPM5-1B) 混合 INT4，RAM/速度更接近现网。

PJZ110（SM8750）没有这份 MiniCPM5 数字。8 Elite OpenCL 对 Qwen 0.6B 已通，**格式上可 spike**，速度/OOM 仍要以真机为准。

## 即便换 MiniCPM5 `.litertlm`，产品缝仍在

可做、但是切片，不是改 URL：

1. Catalog / `ChatModelLayout.MODEL_FILE` / SHA / `sizeLabel` 从 328MB 改为 ~1.55GB；设置确认文案。
2. 首次 GPU init 可能 >10s；现网 0.6B GPU load 已约 4s。
3. **工具**：MiniCPM5 官方是 XML `minicpm5` parser；Dougie 本地是整段 JSON。要么继续用现有 prompt 逼 JSON（2B 可能比 0.6B 稳），要么扩 `LocalToolCallParser`。不可假设换权重工具就自动变好。
4. `localPrompt` 仍只教一小份工具；更大模型可以再教，那是另一份 PRD。
5. Play 仍然不能带 LiteRT。
6. 仍不要把 SCREEN 像素送云；本地视觉不在本调研范围。

## 建议

| 选项 | 建议 |
|---|---|
| 直接用用户链接的 2B-sft INT4 | **否** |
| 侧载加 MLC / 恢复 GGUF 只为 2024 MiniCPM | **否**（与已拍板栈冲突） |
| Spike：`MiniCPM5-2B_int4.litertlm` 替换现网文件名，PJZ110 探针 | **值得**，若接受 ~1.5GB 与更长 load |
| 先 spike MiniCPM5-1B INT4 | **更稳的体积折中** |
| 保持 Qwen3-0.6B | **默认**，直到 2B 真机证明工具 JSON + 延迟可接受 |

真机 spike 不要改 Loop：沿用 `ChatLlmSpikeActivity`，关 thinking，同一句中文闲聊，记录 load / TTFT / 字符每秒 / RSS。通过后再谈 catalog 切换。
