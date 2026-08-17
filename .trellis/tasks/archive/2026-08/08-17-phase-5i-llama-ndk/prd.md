# Phase 5i：llama.cpp NDK JNI

## Goal

在仓库存在 `third_party/llama.cpp` 时，把 `LlamaJni.nativeComplete` 编进 `libllama.so`（CPU、Qwen3 采样参数）。没有该目录时工程仍能编译，行为与 5h 相同（`loadLibrary` 失败 → 引擎未就绪）。**不入库** llama.cpp 源码与 GGUF。

## Background

- 5h 已声明 `System.loadLibrary("llama")` + `nativeComplete`。
- `PRD.md` §6.10：temperature 0.7 / top-p 0.8 / presence-penalty 1.5，thinking off（prompt 在 Kotlin）。

## Requirements

- `tool/system` CMake：仅当 `third_party/llama.cpp/CMakeLists.txt` 存在时启用。
- JNI 实现 `Java_com_dougie_tool_system_LlamaJni_nativeComplete`：加载 GGUF、decode、返回 UTF-8；不把 prompt 打到 log。
- `n_gpu_layers=0`；输出上限短（意图 JSON）。
- `.gitignore` 忽略 `third_party/llama.cpp`。
- 无 llama.cpp 时 `./gradlew :core:tool:test :app:checkChannelLeak` 仍通过。

## Acceptance Criteria

- [x] 无 `third_party/llama.cpp` 时 Play/Sideload 组装与 channel leak 通过。
- [x] JNI 符号与 `LlamaJni.nativeComplete` 一致；CMake 产出库名 `llama`。
