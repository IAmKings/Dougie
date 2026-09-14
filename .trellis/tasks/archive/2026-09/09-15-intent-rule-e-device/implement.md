# Implement: 规则 E 真机采集

## Order

1. 复制 `heldout.jsonl` 到 `core/tool/src/main/resources/intent-corpus/heldout.jsonl`（与 test 一致）。
2. `IntentEval.loadHeldout` / `runForward` / `writeJsonl` + Fake 单测（AC1）。
3. `:app` 采集函数（复用 probe 的 engine 构造），写 `File(filesDir, IntentEval.PREDICTIONS_RELATIVE)`。
4. Debug：注入回调、按钮「评测意图规则 E」、busy + 结果行（无达标文案）。
5. `./gradlew :core:tool:test :feature:debug:testDebugUnitTest :app:checkChannelLeak`

## Do not

- Settings 「已达标」、androidTest、改 held-out 句子、ORT 进 `:feature:debug`。
- 提交 `eval/intent/predictions.jsonl` / onnx。
- `task.py start` 前未获规划批准。
