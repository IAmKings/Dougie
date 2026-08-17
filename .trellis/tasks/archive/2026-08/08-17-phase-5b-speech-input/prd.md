# Phase 5b：离线语音输入合同

## Goal

接入 `speech_input` Tool：Agent 只能拿到转录文本，不能直接碰麦克风。录音权限、前台限制、模型未就位时失败，且音频不出设备、不入日志。本切片 **不** 把 Paraformer 230MB 打进仓库，也 **不** 调用系统/云端识别。

## Background

- 根 `PRD.md` §6.8：sherpa-onnx + Paraformer-zh int8；play 按需下载，sideload 可内置；规则 D 的 CER 集是后续切片。
- TapSwipe 已完成。语音闭环的 TTS / 本地 LLM 不在本任务。

## Requirements

- `SpeechInputTool` 名称 `speech_input`，风险 L1，声明 `RECORD_AUDIO`。
- 应用不在前台：中文错误，且 `listenCount == 0`。
- 模型文件未就位：中文错误，不打开麦克风。
- 模型文件在但引擎未接入：另一条中文错误，仍不打开麦克风。
- 引擎就绪（测试 Fake）：返回 `{"ok":true,"text":"..."}`，JSON 不含 audio/pcm/base64。
- 权限中心可申请麦克风；Play/Sideload 都注册该 Tool（与点击渠道隔离不同）。
- 不新增云端 SpeechRecognizer / 网络 ASR。

## Constraints

- `:core:*` 无 `android.*`。Android 实现放 `:tool:system`。
- Git 不提交 ONNX 模型。
- 不实现 TTS、IntentClassifier、CER 评测集。

## Acceptance Criteria

- [ ] JVM 单测覆盖前台/模型缺失/引擎未就绪/成功文本路径。
- [ ] `PolicyEngine` 在未授权 `RECORD_AUDIO` 时拒绝。
- [ ] `./gradlew :core:tool:test :core:runtime:test :app:checkChannelLeak` 通过（Play 仍无 Accessibility 泄漏）。
