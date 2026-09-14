# Design: Kokoro 规则 B runner

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` `KokoroEval` | JSONL 解析与 `ruleBPassed`；Fake 清单测阈值 |
| testdata | 小型 `kokoro-rtf-sample.jsonl` |
| `eval/tts/` | gitignored；以后放 `kokoro-rtf.jsonl` |
| Catalog / `SherpaJni` / App | **本刀不改** |

## Layout

```
eval/tts/kokoro-rtf.jsonl
```

一行：

```
{"id":"k001","text":"现在几点了","synthMs":820,"audioDurationMs":1000,"numThreads":1,"naturalnessOk":true}
```

`synthMs` / `audioDurationMs` / `numThreads` / `naturalnessOk` 可缺。

## Contracts

```
data class KokoroEvalItem(id, text, synthMs, audioDurationMs, numThreads, naturalnessOk)
data class KokoroEvalReport(
  nLabeled, nScored, nUnscored,
  p95Rtf, threadsApplied, naturalnessApplied, ruleBPassed,
)
object KokoroEval {
  const val MIN_N = 5
  const val RTF_LIMIT = 1.0
  const val MANIFEST_RELATIVE = "eval/tts/kokoro-rtf.jsonl"
  fun loadJsonl(text: String): List<KokoroEvalItem>
  fun report(items: List<KokoroEvalItem>): KokoroEvalReport
  fun rtf(synthMs: Long, audioDurationMs: Long): Double
}
```

- scored = `synthMs != null && audioDurationMs != null && audioDurationMs > 0 && synthMs >= 0`
- rtf = synthMs / audioDurationMs（double）
- p95Rtf：scored rtf 升序，nearest-rank `sorted[ceil(0.95 * n) - 1]`；n=0 → 0
- `threadsApplied` = nScored>0 且每条 scored 的 `numThreads == 1`
- `naturalnessApplied` = nScored>0 且每条 scored 的 `naturalnessOk == true`
- `ruleBPassed` = nLabeled≥5 ∧ nScored≥5 ∧ p95Rtf≤1.0 ∧ threadsApplied ∧ naturalnessApplied
- `toString()` 只打 counts/rates，永不打 `text`

## Compatibility

CI、VITS catalog、`speech_output`、系统 TTS 降级不变。不改 `UserFacingErrors`。

## Risks

现网 TTS 是 2–4 线程 VITS。以后真测 Kokoro 必须另开 `numThreads=1` 的合成路径，不能拿当前 `SherpaJni.threadCount()` 填表。公开 RPi RTF 2.7–6.6 很可能会让 `ruleBPassed` 长期为 false——这是预期，产品继续 VITS。
