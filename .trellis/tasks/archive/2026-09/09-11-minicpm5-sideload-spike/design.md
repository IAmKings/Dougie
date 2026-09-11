# Design: MiniCPM5 INT4 侧载探针

## Outcome

PJZ110 上 MiniCPM5 `.litertlm` 的 CPU/GPU 度量 + 本任务 `research/pjz110-minicpm5.md`。产品 Chat 仍用 Qwen3-0.6B catalog。

## Engine

不换运行时。Sideload 已有 LiteRT-LM 0.16.1；MiniCPM5 社区卡要求 ≥ 0.16 与 thought channel。探针已 `ThinkingConfig(false)`（INT4 开 think 会拖死预算）。

不引入 MLC / llama.cpp。2024 MiniCPM-2B-sft 不在候选名单。

## Probe directory

`ChatLlmProbe.findModel` 取目录中字典序最小的 `.litertlm`。`MiniCPM5-2B_int4.litertlm`（`M`）会排在 `Qwen3-…`（`Q`）前面；1B 的 `minicpm_wi4b32_…`（`m`）会排在 Qwen **后面**。因此：

- 测某一档时，该目录只保留那一个 `.litertlm`。
- 测完 2B 再测 1B 前删掉 2B 文件。
- 任务结束后把产品 Qwen 文件拷回，避免下次 Chat 因缺 catalog 文件名而不就绪。

外部路径（与 09-04 相同，避免 `run-as`）：

`/sdcard/Android/data/com.dougie.app.sideload/files/models/chat/`

## Weights

| 档 | URL | 约体积 |
|---|---|---|
| 先 | `https://huggingface.co/litert-community/MiniCPM5-2B/resolve/main/MiniCPM5-2B_int4.litertlm` | 1.55 GB |
| 1B 对照（用户加测） | `https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm` | 793 MB |

1B 非 gpu_opt 的同体积文件不再作为本切片主测：产品引擎先 GPU，2B 真机也是 GPU 才达到可用体感。CPU 仍跑一遍 gpu_opt，看降级是否还能出字。

## Failure / fallback

2B「失败」= CPU 与 GPU 都没有「成功」态（非空输出且未超时）。进程被杀、`initialize` 抛错、30s 生成超时、空输出都算该后端失败。

一侧成功一侧失败：记两边数字，**不**下载 1B。

生成超时若挡住 2B 结论，允许把 `GENERATE_TIMEOUT_MS` 略增，并在 research 写新值。不要为了工具 JSON 改 prompt。

## Product isolation

`ChatLlmProvider` 读的是 `ChatModelLayout.MODEL_FILE` 固定名。探针目录里的 MiniCPM 文件**不会**让 Loop 用上 MiniCPM，除非有人把文件改名为 Qwen 那个名字。禁止改名冒充 catalog。

## Rollback

删探针目录里的 MiniCPM 文件；恢复 Qwen `.litertlm`。无 catalog / SHA 回滚。
