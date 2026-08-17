# Phase 5e：语音输出合同与系统降级

## Goal

接入 `speech_output`：把短文本念出来。离线 VITS 未就绪时用系统 TTS 降级；拒绝需要联网的系统音色。本切片 **不** 提交 116MB VITS 模型，也 **不** 做 Kokoro / 本地 LLM。

## Background

- 根 `PRD.md` §6.9：主选 sherpa VITS，play 按需下载，sideload 可内置；系统 TTS 仅短提示降级，且不得走在线引擎。
- ASR JNI 已接入；TTS JNI/VITS 文件布局留给后续切片。

## Requirements

- Tool 名 `speech_output`，风险 L0，参数 `text`（非空）。
- 离线引擎就绪时只走离线，不调用系统 TTS。
- 离线未就绪：系统降级，且 `text` 超过短提示上限则失败、不播。
- 系统音色若 `isNetworkConnectionRequired`：失败，中文说明禁止联网播报。
- 成功 JSON：`ok` + `backend`（`offline` 或 `system`），不含音频字节。
- 不入库 ONNX。

## Acceptance Criteria

- [x] JVM：离线优先、降级路径、超长降级失败、空文本拒绝。
- [x] `./gradlew :core:tool:test :app:checkChannelLeak` 通过。
