# Phase 5l：sideload 内置语音模型

## Goal

**sideload** 渠道把 ASR + TTS 模型随包携带，首次启动静默拷到 `filesDir`，使离线语音闭环一次到位。**play** 包仍不含模型文件。意图 GGUF **不**内置（规则 A 预算；继续走 5k 按需下载）。**不**做评测集。

## Background

- 根 `PRD.md` §6.8–6.9、规则 A：sideload 语音内置 ≤400MB（ASR≤230MB + TTS≤120MB）；play 按需下载。
- §6.10：意图为独立可选模块，不计入语音预算。
- 5k 设置页三行在文件已存在时显示已安装。`*.onnx` / `*.gguf` 已 gitignore。
- sideload sourceSet 已有 Accessibility；尚无 models assets。

## Requirements

- 仅 `sideload` sourceSet 可包含 `models/asr`、`models/tts` 布局文件；play 的 APK/assets **不得**出现这些路径或 `.onnx`。
- 首次启动（或 layout 缺失时）从 sideload assets 拷到 `filesDir` 对应目录；已完整则跳过。不覆盖用户已下好的完好文件。
- 仓库不强制提交真实大模型；本地把文件放到约定目录即可打进 sideload。CI 用 seeder 单测 + play 泄漏检查，不依赖 230MB 文件。
- 意图模型不打进 APK。
- 产品名 Dougie。

## Out of scope

- CER / 意图评测集
- SenseVoice / Kokoro / VAD 内置
- 意图 GGUF 打进 sideload
- 改 play 下载 UI

## Acceptance Criteria

- [x] play Debug APK 不含 `models/asr`、`models/tts`、`.onnx` 模型资源。
- [x] seeder：assets 齐全时拷到 layout 可 `isPresent`；已安装则不覆盖；缺文件则不假装成功。
- [x] `./gradlew :app:checkChannelLeak`（JDK 17）通过，并含 play 无模型泄漏断言。
