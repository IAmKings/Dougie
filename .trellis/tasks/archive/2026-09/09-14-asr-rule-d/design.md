# Design: ASR 规则 D runner

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` | Manifest parse、`AsrEvalReport`、`FullEvalSet` 计数；Fake 清单测阈值 |
| testdata | 现有 5 条 `asr-gold.json` 不变；另加小型 manifest fixture |
| `eval/asr/` | gitignored；用户以后放 wav + `manifest.jsonl` |
| App / sherpa | **本刀不调用** |

## Layout

```
eval/asr/manifest.jsonl   # 每行一条 JSON
eval/asr/wav/<id>.wav     # 可选，本刀不读音频
```

一行：

```
{"id":"d001","reference":"现在几点","wav":"wav/d001.wav","hypothesis":"现在几点","vadOk":true}
```

`hypothesis` / `vadOk` / `wav` 均可缺。缺 hypothesis 的条目不进入 CER 平均，只计 `unscored`。

## Contracts

```
data class AsrEvalItem(id, reference, wav, hypothesis, vadOk)
data class AsrEvalReport(
  nLabeled, nScored, nUnscored,
  meanCer, successRate,
  vadApplied, ruleDPassed,
)
object AsrEval {
  fun loadJsonl(text: String): List<AsrEvalItem>
  fun report(items: List<AsrEvalItem>): AsrEvalReport
}
```

- success = scored 且 CER 对该条 ≤ 0.05 且 `vadOk==true`（vadApplied 时）
- `vadApplied` = 每条 scored 都有非空 `vadOk`
- `ruleDPassed` = nLabeled≥500 ∧ nScored≥500 ∧ meanCer≤0.05 ∧ successRate≥0.95 ∧ vadApplied
- `FullEvalSet.isPresent` 可保持「有 wav」；新增 `labeledCount` / 文档说明 500 条看 manifest

`:cli` 可选：读文件打印 report；缺文件打印 skip。不要为了 runner 给 Play 加 UI。

## Compatibility

CI 与现网 Chat 行为不变。不改 UserFacingErrors。

## Risks

有 wav 无 hypothesis 时报告 unscored 很高——这是预期，直到后续接转写。
