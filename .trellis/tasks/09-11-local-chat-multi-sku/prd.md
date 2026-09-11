# 侧载多档对话模型共存与激活

## Goal

侧载设置可分别下载 Qwen3-0.6B、MiniCPM5-1B gpu_opt、MiniCPM5-2B INT4，三档文件共存；用户显式激活一档后，本地 Chat 只跑那一档。Play 仍无对话下载。

## Background

- 现网：单 offer `id=chat`，固定文件名 `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`，`ChatLlmProvider` 进程内引擎只开这一份。`ModelImporter` 对 pack 做 SHA 一一对应，同目录多一个 `.litertlm` 扫描会失败。
- PJZ110 探针（`.trellis/tasks/archive/2026-09/09-11-minicpm5-sideload-spike/research/pjz110-minicpm5.md`）：三档 GPU 均能闲聊。1B gpu_opt 加载 ~7s / ~47 字/秒；2B ~15s / ~33 字/秒；0.6B ~4s / ~26 字/秒。
- 1B SHA-256（真机）：`4e15a7cc735ae36e9888d341d44bd53c0579153d7a3379aaf958620eea4816e3`
- 2B SHA-256：`9858563beafbc6d5e0d25fcee3827541515296a9302ed3d088b16a58d4fbe7b8`
- 用户 2026-09-11 认可：共存、显式激活、三套 HTTPS、下载完成不自动切换。

## Requirements

- R1 侧载设置三行对话 SKU（标题中文）：`chat-qwen06` / `chat-minicpm1b` / `chat-minicpm2b`。各有 HTTPS、SHA、`sizeLabel`（约 328MB / 756MB / 1.5GB）。Play `AppOfflineModels` 仍去掉全部对话行。
- R2 三档文件名保持 HF 原名，写入 `filesDir/models/chat/` 与外部树同一目录，互不覆盖。导入只核对本 SKU 的文件名+SHA；兄弟 `.litertlm` 允许存在。对话下载确认不得声称覆盖其它对话包。
- R3 `PreferenceStore.activeChatSku`。未装则不可激活。下载完成不自动切换。仅一档已装时启动可默认激活该档。设置：未装→下载；已装未用→使用；当前→使用中。切换时若有进行中的本地生成则拒绝或等结束；必须关掉现有 LiteRT Engine 再开新路径。
- R4 `localLlmReady` / 引擎路径 = 激活档文件存在且非空。目录里有其它包不等于就绪。
- R5 同时只下一档对话包。确认文案提示三档合计约 2.6GB。
- R6 `ThinkingConfig(false)` 不变。不改 MiniCPM XML 工具解析；本地仍 JSON prompt。
- R7 Play `checkChannelLeak` 过。不把权重打进 APK。不读 `/sdcard/Dougie` 作为运行路径。

## Acceptance Criteria

- [ ] AC1 侧载三行均可独立下载安装；已装 0.6B 再下 1B 不删除 0.6B。
- [ ] AC2 激活 1B 后 Chat 本地走 1B 文件；再激活 0.6B 下一句走 0.6B（引擎已重建）。
- [ ] AC3 只装未激活时 `localLlmReady` 为 false（除非唯一已装自动激活）。
- [ ] AC4 Play 无对话行、无 LiteRT 泄漏；`checkChannelLeak` 过。
- [ ] AC5 JVM：catalog SHA/URL、导入允许多文件、激活 prefs、引擎路径。

## Out of scope

- NPU、按电量自动换模、1B/2B 多教工具、从 SAF 路径直接推理、探针 UI 做设置入口。
