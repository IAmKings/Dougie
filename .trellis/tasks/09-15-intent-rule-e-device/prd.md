# 意图规则 E 真机采集

## Goal

在已安装 MiniRBT 意图包的设备上，对仓库自建 88 条 held-out 做真 `classify`，把预测与单次延迟写入 `filesDir/eval/intent/predictions.jsonl`，再用现有 `IntentEval.ruleEReport` 得到规则 E 数字。缺包时跳过并中文说明，不宣称达标。

## User value

第一次能在 PJZ110 上核对准确率与 P95，而不是罐头 JSON 的 10/11。

## Background

- `IntentEval.ruleEReport` 已归档：`ruleEPassed` 需 ≥10 类、88 条 scored、准确率 ≥90%、每条 `latencyMs`、P95 ≤500ms。
- 金标：`heldout.jsonl` 88 条、11 类、每类 8 条，现于 `:core:tool` **main** `intent-corpus/heldout.jsonl`（与 test / `scripts/intent/` 一致，进 APK 供真机采集）。
- 设置「测试」只 `classify("现在几点")`。`:feature:debug` 只依赖 `:core:runtime` / `:core:model`，不调 JNI。
- `IntentEval.timedClassify` 已可注入 `IntentEngine`。`:core:*` 禁止 ORT。`/eval/` gitignore。开发者页 Play/侧载都有。

## Requirements

- R1 把 held-out 放进 `:core:tool` **main** 资源（与 test 内容一致，无 PII），供 JVM 与 APK 读取。不改 88 条文本。
- R2 JVM 增加采集器：读 held-out → 对每条 `timedClassify` → 写 JSONL（`id`/`text`/`goldIntent`/`predictedIntent`/`latencyMs`）→ `ruleEReport`。Fake 引擎可测；不调 ORT。
- R3 `:app` 用现网 `OnnxIntentEngine` + `IntentOrtJni`（与 `AppOfflineModelProbe` 同缝）在 `Dispatchers.Default` 跑采集；写出 `filesDir/eval/intent/predictions.jsonl`。缺包/引擎未就绪用现有 `INTENT_MODEL_MISSING` / `INTENT_ENGINE_NOT_READY`。
- R4 设置 → **开发者** 增加一键「评测意图规则 E」；进行中不可重入。完成后只展示 `IntentRuleEReport.toString()`（counts/rates）与相对路径，不展示 utterance / 意图名 / 「已达标」。
- R5 不改 Chat 路由、`MIN_CONFIDENCE`、catalog、设置模型行文案。不新建 androidTest。不把 jsonl 提交进 git。

## Out of scope

- 设置页「准确率已达标」、微调 MiniRBT、改 held-out、仪器测试框架。
- 规则 D / Kokoro、SenseVoice、松手自动 submit。
- 把 `filesDir` 结果自动拷到仓库根 `eval/`。

## Technical notes

- `:feature:debug` 只收 `:app` 注入的 `suspend () -> String`（或等价结果类型），不依赖 `:core:tool` / JNI。
- 日志与 UI 禁止 utterance 与标签。classify 失败的条目不写 `predictedIntent`（unscored）。

## Acceptance Criteria

- [x] AC1 Fake 跑 88 条：写出合法 JSONL，`nScored=88`，报告 `toString()` 不含金标原文。
- [x] AC2 缺意图包：开发者页显示现有未就绪文案，不写文件、不崩溃。
- [x] AC3 有包时（真机）：生成 88 行 jsonl；`ruleEPassed` 随真实准确率/P95 计算；UI 无「已达标」。
- [x] AC4 `:feature:debug` 仍不引入 ORT/系统 TTS；`:core:tool:test` 与 `checkChannelLeak` 过。
- [x] AC5 git 无 onnx、无 `eval/intent/predictions.jsonl`。

PJZ110（现网 MiniRBT）：`nLabeled=88` `nScored=88` `accuracy=0.9318`（82/88）`p95Ms=17` `ruleEPassed=true`。`predictions.jsonl` 留在 `filesDir`，不入库。设置页仍不写「已达标」。
