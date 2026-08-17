# Phase 5f：sherpa VITS 离线合成

## Goal

在 **VITS 模型文件 + `libsherpa-onnx-jni` 均可加载** 时，`speech_output` 走离线合成并本地播放，不再调用系统 TTS。缺少模型或 `.so` 时保持 5e 行为（系统短提示降级）。ONNX **不入库**。本切片 **不** 做 Kokoro、sideload APK 内置打包、Play 按需下载、本地 LLM。

## Background

- 5e 已有 `SpeechOutputTool` + `PreferOfflineTtsPort`；离线默认 `UnwiredTtsEngine`。
- 根 `PRD.md` §6.9：主选 `vits-zh-hf-fanchen-C`（~116MB），与 ASR 同框架 sherpa-onnx。
- ASR 已用同一 JNI 库；TTS 需补 trimmed `OfflineTts` 绑定（v1.13.4）。

## Requirements

- `SherpaTtsEngine`：`filesDir/models/tts/` 不齐或 native 不可用 → `isReady()==false`。
- 两者都齐：合成并播放，成功 `backend=offline`。
- 布局：`model.onnx` + `tokens.txt` + `lexicon.txt`（与官方 VITS 包一致）；`dict/` 若存在则传入 `dictDir`。
- 不提交 `*.onnx` / `jniLibs/**`。
- 不接在线 TTS / Kokoro。
- 不把播报文本写入 Logcat / AuditLog。

## Acceptance Criteria

- [x] JVM：缺模型 / 缺 native 时不合成；齐备时合成回调一次且结果无 pcm。
- [x] 离线就绪时 `PreferOfflineTtsPort` 不走系统 TTS。
- [x] `./gradlew :core:tool:test :app:checkChannelLeak` 通过。
