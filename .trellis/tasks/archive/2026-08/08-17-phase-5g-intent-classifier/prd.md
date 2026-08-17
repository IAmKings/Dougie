# Phase 5g：本地意图分类合同

## Goal

接入 `intent_classifier`：Agent 把用户文本交给本地意图分类，拿到结构化 JSON。模型或引擎未就绪时失败，不得静默改走云端。本切片 **不** 提交 GGUF，也 **不** 接入 llama.cpp。

## Background

- 根 `PRD.md` §6.10：Qwen3-0.6B-Instruct，独立可选下载，不计入语音包体预算。
- 语音闭环（ASR/TTS）已完成；本切片只做 Tool 合同与文件/引擎门。

## Requirements

- Tool 名 `intent_classifier`，风险 L0，参数 `text`（非空）。无需额外 Android 权限。
- 模型文件未就位：中文错误，不推理。
- 模型在但引擎未接入：另一条中文错误。
- 置信度低于阈值：失败，提示改用云端或补充说明，不得静默猜测。
- 成功 JSON：`ok` + `intent` + `slots` + `route` + `confidence`；不含模型权重/prompt。
- 输入文本与意图结果不入 Logcat / AuditLog。
- 不入库 `*.gguf`。

## Acceptance Criteria

- [x] JVM：缺模型 / 缺引擎 / 低置信度失败；成功路径 JSON 形状正确。
- [x] `./gradlew :core:tool:test :app:checkChannelLeak` 通过。
