# Phase 5d：sherpa-onnx 本地转写

## Goal

在 **模型文件 + `libsherpa-onnx-jni` 均可加载** 时，把前台 PCM 交给 Paraformer 离线转写。缺少 `.so` 时行为与 5c 相同（引擎未就绪、不开麦）。ONNX 与 JNI 二进制 **不入库**。

## Background

- 5c 已有 `SpeechSession` / `AudioRecord` / Paraformer 文件布局。
- 官方 Kotlin API 通过 JNI `OfflineRecognizer` 读本地 `model.int8.onnx` + `tokens.txt`。
- 官方 Android `.so` 来自 GitHub Release 的 android tarball，体积大，不适合 git。

## Requirements

- `SherpaSpeechEngine`：模型目录不齐或 native 不可用 → `isReady()==false`。
- 两者都齐：`transcribe` 调用注入的 decode，返回纯文本。
- Android 默认引擎走 `System.loadLibrary("sherpa-onnx-jni")` + Paraformer `newFromFile`；库不存在则永不引用 JNI 类。
- 不提交 `*.onnx` / `jniLibs/**`。
- 不接云端 STT，不实现 TTS。

## Acceptance Criteria

- [ ] JVM：缺模型 / 缺 native 时不 decode；齐备时 decode 一次且结果无 pcm。
- [ ] `./gradlew :core:tool:test :app:checkChannelLeak` 通过。
