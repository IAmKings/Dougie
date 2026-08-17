# Phase 5m：离线评测夹具

## Goal

提供可在 CI 跑的 **CER** 与 **意图准确率** 计算夹具，对照根 `PRD.md` 规则 D（CER ≤ 5%）与规则 E（意图 ≥ 90%）。本片交付**公式 + 小样本金标 + 阈值报告**，**不**把 ≥500 条真音频或 GGUF/ONNX 打进仓库，**不**在 CI 上跑真机 sherpa/llama。

## Background

- 规则 D：自建普通话集 ≥500 条、CER ≤ 5%、含 VAD 端到端成功率 ≥ 95%。
- 规则 E：10+ 意图类别、准确率 ≥ 90%。
- 引擎与模型已在 5d–5i；本片缺的是可重复的度量代码。

## Requirements

- JVM：`cer(hypothesis, reference)`（字级编辑距离 / 参考长度）；空参考单独定义。
- 小样本 ASR 金标（文本对，数条即可）跑 CER 并打印是否 ≤ 5%。
- 小样本意图金标（≥10 个 intent 标签出现在夹具 schema 中；条目可少于 500）对 `IntentJsonParser` 输出比 intent 字段，打印是否 ≥ 90%。
- 大集路径约定 gitignore（如 `eval/asr/*.wav`），缺失时测试不失败，只跳过「全量」任务。
- 不入库 wav/onnx/gguf；不改 Tool 合同。

## Out of scope

- 录制或提交 500 条音频
- 真机 RTF / P95 延迟
- SenseVoice 切换、VAD 端到端 95% 实跑
- 把评测接到 Chat UI

## Acceptance Criteria

- [x] JVM 单测覆盖 CER 公式（全对 0、替换/插入/删除）。
- [x] 意图夹具：解析 JSON 后按 intent 计准确率。
- [x] `./gradlew :core:tool:test`（JDK 17）通过，不依赖本地模型文件。
