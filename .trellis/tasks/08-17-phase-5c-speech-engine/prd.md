# Phase 5c：离线 ASR 采集与引擎缝

## Goal

让 `speech_input` 在模型与引擎都就绪时，于前台录一段 16kHz PCM 并交给可注入的本地 `SpeechEngine` 转写。模型布局对齐 Paraformer-zh（`model.int8.onnx` + `tokens.txt`）。本切片 **不** 把 ONNX 或 sherpa JNI 提交进 git，也 **不** 调用云端识别。

## Background

- 5b 已有权限、前台、模型门；`AndroidSpeechPort.isEngineReady()` 恒为 false。
- 官方 Paraformer 目录是 `model.int8.onnx` + `tokens.txt`，不是单独的 `encoder.onnx`。
- sherpa-onnx 官方 AAR 需本地组包；JNI `.so` 体积大，不入库。

## Requirements

- `SpeechSession`（JVM）组合 recorder + engine；未过 Tool 门时不得 `capture`。
- 引擎就绪路径：recorder 被调用一次，结果只有 `text`，无 pcm/base64。
- 空采样返回中文错误，不把音频写入日志。
- 模型就位 = 私有目录同时存在非空 `model.int8.onnx` 与 `tokens.txt`。
- Android 用 `AudioRecord` 采集最多约 3 秒；默认引擎未接入则 `isEngineReady()==false`，不打开麦克风。
- 不提交 `*.onnx` / `jniLibs`。

## Constraints

- `:core:*` 无 `android.*`。`AudioRecord` 仅 `:tool:system`。
- 不实现 TTS、CER 评测、模型下载 UI。

## Acceptance Criteria

- [ ] JVM 测试：就绪时 capture+transcribe 各一次；门禁路径 captureCount==0。
- [ ] 空 utterance 失败且 JSON 无音频字段。
- [ ] `./gradlew :core:tool:test :app:checkChannelLeak` 通过。
