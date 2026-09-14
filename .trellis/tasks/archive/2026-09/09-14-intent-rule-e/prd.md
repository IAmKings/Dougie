# 意图规则 E 评测定标

## Goal

给规则 E 一个可重复、CI 不误报的 JVM 评测契约：现网 MiniRBT 在自建 11 类 held-out 上准确率 ≥ 90%，目标机单次 classify P95 ≤ 500ms。缺预测或延迟时跳过、不算达标。

## User value

不定标前不宣称离线意图「达标」。有预测文件时数字可核对，不再用罐头 JSON 的 10/11 假装规则 E 过线。

## Depends on

`09-14-asr-rule-d` 已归档。不改 ASR 评测集。下一刀 `09-14-kokoro-rtf` 仍等本刀归档。父任务：`09-14-phase-5-calibration`。

## Background

- PRD §6.10：≥10 类自建集准确率 ≥ 90%；目标机 P95 ≤ 500ms；低置信澄清或回云。
- `IntentEval` 现测 JSON 解析器 + `intent-gold.json` 11 条罐头 `modelJson`（10/11 → `passed=true`），不是 MiniRBT 前向。
- 金标已在仓库：`heldout.jsonl` **88 条、11 类、每类 8 条**（`IntentCorpusTest`；与 train 无重叠）。不另造集、不微调。
- catalog `intent-minirbt-v2`；Python ORT 曾报 int8 **81/88**。真机 P95 从未进门禁。`:core:*` 禁止调 ORT。仓库无 androidTest。
- `IntentClassifierTool` 已在 `confidence < 0.5` 时 `INTENT_LOW_CONFIDENCE`。`/eval/` 已 gitignore。

## Requirements

- R1 在 `IntentEval` 增加与 `AsrEval` 同构的前向 JSONL 报告（保留现有解析器 `loadItems` / `report` / `passed`）。字段：`id`、`text`、`goldIntent`，可选 `predictedIntent`、`latencyMs`。缺预测 → unscored，不进准确率。`toString()` 只打 counts/rates，不打 utterance / 标签。
- R2 `ruleEPassed` = nClasses≥10 ∧ nLabeled≥88 ∧ nScored≥88 ∧ accuracy≥0.90 ∧ p95Ms≤500 ∧ latencyApplied。scored = 有 `predictedIntent`。任一 scored 行缺 `latencyMs` → `latencyApplied=false` → 不能过。
- R3 解析器夹具与 `passed` 保留；**不是** 规则 E 达标。
- R4 缺 `eval/intent/predictions.jsonl` 时 `:core:tool:test` 仍过。合成 ≥88 条可翻转阈值。可注入 `IntentEngine.classify` 计时（Fake 测契约）；本刀不跑 PJZ110、不写仪器测试。
- R5 不训练、不改 held-out、不提交 onnx、不改 Chat 低置信、设置页不出现「意图已达标」。

## Out of scope

- PJZ110 实跑 88 条、androidTest、设置页采集 UI。
- 微调 / 重导出 MiniRBT；新增意图类；改 `MIN_CONFIDENCE`。
- 把 Python 桌面 ORT 延迟当作 P95。
- SenseVoice、规则 D、Kokoro、松手自动 submit。

## Technical notes

- 布局：gitignore `eval/intent/predictions.jsonl`；testdata 仅几行 sample。
- P95：scored 且有 `latencyMs` 的值升序，nearest-rank `sorted[ceil(0.95 * n) - 1]`。
- 准确率只比标签相等，不把 `MIN_CONFIDENCE` 算进 `ruleEPassed`。

## Acceptance Criteria

- [x] AC1 缺 predictions 或不足 88 条 scored：现有 `:core:tool:test` 仍过；`ruleEPassed=false`。
- [x] AC2 合成 ≥88 条带 predictedIntent + latencyMs：数字正确且 `ruleEPassed` 随准确率 / P95 / 类数翻转。
- [x] AC3 有预测无延迟：不算通过。
- [x] AC4 少于 10 个 distinct `goldIntent`：不算通过。
- [x] AC5 git 无 onnx；产品文案不出现「意图已达标」；解析器 `passed` 不改口成规则 E。
