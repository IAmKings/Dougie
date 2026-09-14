# Design: 规则 E 真机采集

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` | held-out main 资源、`IntentEval` 读金标 / `timedClassify` 循环 / 写 JSONL / `ruleEReport` |
| `:app` | `OnnxIntentEngine` + JNI，写 `filesDir/eval/intent/predictions.jsonl` |
| `:feature:debug` | 按钮 + 展示 counts 字符串；**不**调 classify |
| Settings 测试 | 仍只烟测「现在几点」 |

## Data flow

```
heldout.jsonl (main resource)
  → IntentEval.loadHeldout()
  → timedClassify(engine, text) × 88
  → predictions.jsonl + IntentRuleEReport
  → Debug UI shows report.toString() + relative path
```

设备路径：`filesDir/eval/intent/predictions.jsonl`（与 `IntentEval.PREDICTIONS_RELATIVE` 一致）。拉回本机后可放到 gitignored 仓库根 `eval/` 再打分；本刀不自动同步。

## Contracts

```
IntentEval.loadHeldout(resource): List<IntentPredItem>  // gold only; id = h001…
IntentEval.runForward(engine, items): Pair<List<IntentPredItem>, IntentRuleEReport>
IntentEval.writeJsonl(file, items)
```

`runForward` 每条独立 try：`AgentException` → 该条无 predicted/latency。不打 Logcat。

`:app` 注入：

```
suspend fun runIntentRuleE(): String  // report.toString() 或 UserFacingErrors.*
```

缺包 throw/return 现有中文错误，Debug 原样显示。

## Compatibility

Chat、catalog、probe 文案不变。Play 与 sideload 都有开发者入口。

## Risks

88 次前向可能数秒；按钮须 disable。Intent JNI 与 TTS 若共享锁，采集期间按住说话可能排队——可接受，不改锁。
