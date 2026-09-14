# 意图规则 E 评测定标

## Goal

对现网 MiniRBT 意图包按 PRD §6.10 规则 E：≥10 类自建集准确率 ≥ 90%，目标机 P95 ≤ 500ms。低置信仍澄清或回云，不静默猜。

## Depends on

`09-14-asr-rule-d` **归档后**再 `task.py start`。不改 ASR 评测集。

## Background

- `IntentEval` 现测的是 **JSON 解析器** + 11 条罐头 `modelJson`，不是真机前向。
- `intent-gold.json` schema 已有 11 类（含 unknown）。
- 父任务：`09-14-phase-5-calibration`。下一刀 `09-14-kokoro-rtf`。

## Notes

held-out 短语集、是否现场微调分类头，规划本刀时再定。权重不进 git。
