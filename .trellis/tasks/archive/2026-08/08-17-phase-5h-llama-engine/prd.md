# Phase 5h：llama 意图引擎缝

## Goal

在 **GGUF + `libllama` 均可加载** 时，把用户文本交给可注入的本地完成函数，解析 JSON 意图。缺 `.so` 时行为与 5g 相同（引擎未就绪、不推理）。GGUF 与 llama.cpp 源码 **不入库**。本切片 **不** 写 NDK/CMake、**不** 做准确率评测集。

## Background

- 5g 已有 `IntentClassifierTool` / `UnwiredIntentEngine`。
- `PRD.md` §6.10：Qwen3-0.6B-Instruct，non-thinking JSON 输出；低置信度已在 Tool 层处理。

## Requirements

- `LlamaIntentEngine`：模型目录不齐或 native 不可用 → `isReady()==false`。
- 两者都齐：`complete` 一次，从模型文本中抽出 JSON → `IntentHit`。
- 非法/非 JSON 输出：中文 `INTENT_FAILED`，不调用云端。
- Android 默认引擎走 `System.loadLibrary("llama")`；库不存在则不调用 native complete。
- 不提交 `*.gguf` / llama.cpp / `jniLibs`。
- 不把 prompt 或用户文本写入日志。

## Acceptance Criteria

- [x] JVM：缺模型 / 缺 native 时不 complete；齐备时 complete 一次且结果无 gguf/prompt。
- [x] 能从夹杂前后文的模型输出中解析 `intent`/`slots`/`route`/`confidence`。
- [x] `./gradlew :core:tool:test :app:checkChannelLeak` 通过。
