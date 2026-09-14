# Design: 意图规则 E runner

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` `IntentEval` | 解析器 gold 保持原 API；新增 JSONL 前向报告与可选 `timedClassify` |
| testdata | `intent-gold.json` 不变；另加小型 predictions sample |
| `eval/intent/` | gitignored；以后放 `predictions.jsonl` |
| App / ORT / JNI | **本刀不调用**；不新建 androidTest |

## Layout

```
eval/intent/predictions.jsonl
```

一行：

```
{"id":"h001","text":"现在几点","goldIntent":"query_time","predictedIntent":"query_time","latencyMs":12}
```

`predictedIntent` / `latencyMs` 可缺。缺预测的条目不计准确率（unscored）。

## Contracts

保留：

```
IntentEvalItem(id, goldIntent, modelJson)
IntentEvalReport(total, correct, accuracy, passed)  // 解析器；passed ≠ 规则 E
IntentEval.loadItems / report
```

新增：

```
data class IntentPredItem(id, text, goldIntent, predictedIntent, latencyMs)
data class IntentRuleEReport(
  nLabeled, nScored, nUnscored, nClasses,
  accuracy, p95Ms, latencyApplied, ruleEPassed,
)
object IntentEval {
  const val MIN_N = 88
  const val MIN_CLASSES = 10
  const val THRESHOLD = 0.90          // 解析器与规则 E 共用
  const val P95_LIMIT_MS = 500L
  const val PREDICTIONS_RELATIVE = "eval/intent/predictions.jsonl"
  fun loadJsonl(text: String): List<IntentPredItem>
  fun ruleEReport(items: List<IntentPredItem>): IntentRuleEReport
  suspend fun timedClassify(engine: IntentEngine, text: String): Pair<String, Long>
}
```

- nClasses = distinct `goldIntent` on **labeled** rows
- scored = `predictedIntent != null`
- accuracy = correct / nScored（标签相等）；nScored=0 → accuracy=0
- `latencyApplied` = nScored>0 且每条 scored 都有非空 `latencyMs`
- p95Ms：仅对有 `latencyMs` 的 scored 行；nearest-rank `sorted[ceil(0.95 * n) - 1]`；n=0 → 0
- `ruleEPassed` = nClasses≥10 ∧ nLabeled≥88 ∧ nScored≥88 ∧ accuracy≥0.90 ∧ latencyApplied ∧ p95Ms≤500
- `timedClassify`：`System.nanoTime` 包一层 `engine.classify`，返回 `(intent, ms)`。测试用 `FakeIntentEngine`。不写 Logcat。
- `IntentRuleEReport.toString()` 只打 counts/rates，永不打 `text` / intent 名

缺文件：测试不读仓库根 `eval/`（与 `AsrEval` 一样用夹具/合成数据）。不把 `FullEvalSet` 改成意图目录。

## Compatibility

CI 与现网 Chat 行为不变。不改 `UserFacingErrors`、`MIN_CONFIDENCE`、catalog SHA。解析器 `passed` 语义不变。

## Risks

有预测无延迟时 accuracy 可能 ≥90% 而 `ruleEPassed=false`——这是预期，直到真机填上 `latencyMs`。Python 81/88 不写入本 runner，不算本刀达标。
