# Implement: 规则 E 复看与取出

## Order

1. `:app` 抽出 `adbPullHint` / `formatRuleEMessage`（纯 `packageName` + `IntentRuleEReport`）。`run` 成功分支改用它。新增 `last(context)`：缺/空/坏/零行 → null。
2. `:app` JVM 测：play 与 sideload 包名分别出现在 `run-as` 行；格式化结果含 counts 与相对路径，不含夹具 utterance / 意图名 / 「已达标」。`last` 用临时文件测缺文件与合法 jsonl（不调 ORT）。
3. `DebugViewModel` 注入 `loadLastRuleE`；`init` 加载；与 `runRuleE` 竞态不覆盖已有 message。`MainActivity` 传入 `AppIntentRuleEEval.last`。
4. `DebugUiStateTest`：样例 message（含 adb 行）仍无「已达标」；无 utterance 字段泄漏。
5. `./gradlew :core:tool:test :feature:debug:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak`

## Do not

- Settings 「测试」并 88 条、androidTest、Share jsonl、剪贴板强制、仓库根 `eval/` CI 红线。
- 改 held-out、catalog、Chat、`MIN_CONFIDENCE`。
- Logcat / UI 打印 jsonl 行。
- `task.py start` 前未获规划批准。
