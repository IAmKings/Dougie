# Implement: 意图规则 E runner

## Order

1. `IntentPredItem` / `IntentRuleEReport` / `IntentEval.loadJsonl` + `ruleEReport`；保留 `loadItems` / `report`。
2. JVM：不足 88 或不足 10 类 → `ruleEPassed=false`；88 条假数据过/不过准确率与 P95；无 `latencyMs` 不通过。
3. `timedClassify` + `FakeIntentEngine`：返回标签与非负 ms，不断言 ORT。
4. testdata `core/tool/src/test/resources/eval/intent-predictions-sample.jsonl`（几行）。仓库根 `eval/` 仍 gitignore。
5. `./gradlew :core:tool:test`

## Do not

- 提交 onnx / 真机 predictions。
- JVM 评测调 ORT / JNI。
- 新建 androidTest、改设置文案、微调、改 held-out。
- 改规则 D / Kokoro。
- `task.py start` 前未获规划批准。
