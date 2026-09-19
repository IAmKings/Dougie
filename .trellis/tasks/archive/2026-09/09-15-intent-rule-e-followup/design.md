# Design: 规则 E 复看与取出

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` `IntentEval` | 已有 `loadJsonl` / `ruleEReport` / `writeJsonl`。本刀不改契约阈值，不读 Android `filesDir`。 |
| `:app` `AppIntentRuleEEval` | 只读 last：读 `filesDir` 文件 → 格式化 counts + 相对路径 + adb 行。`run` 成功结果追加同一 adb 行。包名用 `context.packageName`。 |
| `:feature:debug` | 注入 `loadLastRuleE: suspend () -> String?`；init 填 `ruleEMessage`；仍不依赖 `:core:tool` / JNI。 |
| Settings / Chat / catalog | 不改 |

## Data flow

```
filesDir/eval/intent/predictions.jsonl
  → AppIntentRuleEEval.last(context)      // 缺/空/坏 → null
  → IntentEval.loadJsonl + ruleEReport    // 只走 toString()
  → formatRuleEMessage(packageName, report)
  → DebugViewModel.ruleEMessage
```

重跑仍走现网 `run` → `writeJsonl` → 同一 `formatRuleEMessage`。

## Contracts

```
fun formatRuleEMessage(packageName: String, report: IntentRuleEReport): String
  // report.toString() + "\n" + PREDICTIONS_RELATIVE + "\n" + adbPullHint(packageName)

fun adbPullHint(packageName: String): String
  // "adb exec-out run-as $packageName cat files/$PREDICTIONS_RELATIVE"

suspend fun last(context: Context): String?
  // missing / empty / unreadable / zero items → null; never Logcat file text
```

`:feature:debug` Factory 增加 `loadLastRuleE`。`init` 与 `refresh()` 的 audit 并行；last 完成时若 `ruleEMessage` 已非 null（用户已点评测）则不覆盖。

坏 JSON / 读失败 → `null`（不是 `INTENT_FAILED`）。`run` 的缺包错误语义不变。

## Compatibility

Chat、设置「测试」、catalog SHA、`MIN_CONFIDENCE`、gitignore `/eval/` 不变。Play 与 sideload 都走同一 `packageName` 注入，adb 行自然分叉。

## Risks

- jsonl 含 utterance：只读路径若把 `item.text` 拼进 UI 即违规。格式化必须只吃 `IntentRuleEReport`。
- `run-as` 在未 debug 的 release 包上可能失败——这是取出限制，不是 UI  bug；命令仍展示给开发者。
- 旧 jsonl（本刀之前 `run` 写出、无 adb 行）打开后会经 `last` 重新格式化，带上 adb 行。预期。
