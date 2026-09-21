# Design: Kokoro 规则 B 真机采集

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` | 金标资源、`KokoroEval` 读写 JSONL / `runForward` / 自然度批注、`KokoroEvalLayout` + 评测用 `ModelPack`（**不**进 `OfficialModelCatalog.standard()`） |
| `:tool:system` `SherpaJni` | **新增**单线程 Kokoro `generate`（独立于 VITS `ttsEngine` / `threadCount()`）。产品 `speak` 路径不改 |
| `:app` | 缺包则 `ModelInstaller` 下到 `filesDir/eval/tts/kokoro/`；合成写 jsonl；`last` / 「本批自然度通过」 |
| `:feature:debug` | 按钮 + busy/进度 + counts 字符串；**不**调 sherpa / installer |

## Data flow

```
gold.jsonl (main resource, ≥5 中文)
  → 缺包则 install(eval pack) → filesDir/eval/tts/kokoro/
  → SherpaJni.generateKokoro(numThreads=1) × N
  → kokoro-rtf.jsonl（无 naturalnessOk）+ KokoroEval.report
  → Debug 展示 toString + 路径 + adb
  → 「本批自然度通过」→ 写回 naturalnessOk=true → 再 report
```

`rtf = synthMs / audioDurationMs`。`audioDurationMs = samples.size * 1000L / sampleRate`（sampleRate≤0 则该条 unscored）。

## Contracts

```
KokoroEval.loadGold(): List<KokoroEvalItem>  // id + text only
KokoroEval.runForward(synth): Pair<List<KokoroEvalItem>, KokoroEvalReport>
KokoroEval.writeJsonl(file, items)
KokoroEval.markNaturalnessOk(items): List<KokoroEvalItem>  // scored → naturalnessOk=true
```

`runForward` 每条独立 try：失败 → 该条无时长（unscored）。不打 utterance 到 Logcat。

`:app` 注入（与规则 E 并列）：

```
suspend fun runKokoroRuleB(onProgress: (downloaded: Long, total: Long) -> Unit): String
suspend fun lastKokoroRuleB(): String?
suspend fun markKokoroNaturalnessOk(): String
```

缺包/哈希失败用现有 `MODEL_DOWNLOAD_*` 或新增一条「评测用合成模型尚未就绪」，Debug 原样显示。

## Compatibility

- `TtsModelLayout.DIR` 仍是 `models/tts`。评测包 `relativeDir` 必须不同。
- `OfficialModelCatalog.standard()` / 设置下载列表不加行。
- Play 与侧载都有开发者入口（与规则 E 相同）。

## Risks

- ~310MB 下载 + 单线程合成可能数分钟：按钮 disable + 进度；不宣称超时即达标。
- 移动端 RTF 很可能 >1.0：`ruleBPassed=false` 是预期，产品继续 VITS。
- `espeak-ng-data` 可能是目录树：评测专用解压，不改 catalog installer。
- JNI 锁：采集期间产品 TTS 排队，可接受。
