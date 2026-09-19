# 意图规则 E 评测续作

## Goal

开发者页打开即可看到上次规则 E 的 counts，并给出把 `predictions.jsonl` 从本机取出的 adb 命令；不重跑 88 条也能复看。

## User value

评测结果不再只活在当次会话内存里。离开开发者页再进来仍能看到数字；需要做 JVM 对账时知道怎么把文件拉下来，且界面不泄露 utterance。

## Background

- 测量门已闭合：PJZ110 MiniRBT `nLabeled=nScored=88`，`accuracy=0.9318`，`p95Ms=17`，`ruleEPassed=true`（`09-15-intent-rule-e-device`）。
- `AppIntentRuleEEval.run` 成功后写 `filesDir/eval/intent/predictions.jsonl`，UI 展示 `IntentRuleEReport.toString()` + `eval/intent/predictions.jsonl`。`ruleEMessage` 只在 `DebugViewModel` 内存；Debug 路由级 ViewModel 离开即销毁。
- jsonl 含 `text` / 标签，`/eval/` gitignore。UI 与 Logcat 禁止 utterance、意图名、「已达标」。
- `:feature:debug` 只收 `:app` 注入的 `suspend () -> String`，不依赖 `:core:tool` / ORT。
- `IntentEval.loadJsonl` / `ruleEReport` 已有。设置「测试」仍只 `classify("现在几点")`。CI 不跑 MiniRBT。

## Requirements

- R1 打开开发者页时，若 `filesDir/eval/intent/predictions.jsonl` 存在且能解析出至少一行：只读 `loadJsonl` + `ruleEReport`，展示与成功 `run` 相同结构的 counts + 相对路径。不调用 `classify` / ORT。
- R2 缺文件、空文件、坏 JSON：不崩溃，不展示原文；`ruleEMessage` 保持空（与从未评测一致）。不把坏文件映射成「意图分类失败」。
- R3 成功复看与成功重跑的文案都追加一行 adb 取出命令，使用当前 `applicationId`（play `com.dougie.app` / sideload `com.dougie.app.sideload`）：`adb exec-out run-as <id> cat files/eval/intent/predictions.jsonl`。不 Share jsonl 正文，不强制剪贴板。
- R4 按钮「评测意图规则 E」仍覆盖重跑；进行中不可重入。重跑进行中或已有结果时，异步 last 不得覆盖更新的 `ruleEMessage`。
- R5 不改 Chat 路由、`MIN_CONFIDENCE`、catalog、设置「测试」、不下「已达标」。不提交 jsonl / onnx。不新建 androidTest。不把仓库根 `eval/` 读进 CI 红线。

## Out of scope

- 把 88 条并进设置「测试」。
- 仓库根 jsonl 的 JVM 读盘提示 / 因 `ruleEPassed=false` 弄红 CI。
- 微调 MiniRBT、改 held-out、androidTest、规则 D / Kokoro、松手自动 submit。
- CI 调 ORT；Share 或导出 jsonl 正文。
- 重做离线模型下载 / 更新。

## Technical notes

- 只读入口放 `:app` `AppIntentRuleEEval`（能读 `filesDir` + `packageName`）。`:feature:debug` 再注入一条 `suspend () -> String?`（或等价），仍不依赖 `:core:tool`。
- 展示字符串必须走 `IntentRuleEReport.toString()`，禁止把 jsonl 行拼进 UI。
- `run-as` 的 cwd 是 `/data/data/<applicationId>/`，相对路径为 `files/` + `IntentEval.PREDICTIONS_RELATIVE`。

## Acceptance Criteria

- [x] AC1 设备上已有合法 jsonl、不点按钮：打开开发者页出现 counts + 相对路径 + 当前包名的 adb 行；无 utterance / 意图名 / 「已达标」。
- [x] AC2 无文件或坏文件：页面不崩，无 counts 块，无文件原文。
- [x] AC3 点按钮覆盖重跑后，文案仍含 counts + 相对路径 + 同一 adb 行；busy 不可重入。
- [x] AC4 Fake / 夹具：合法 jsonl → 格式化字符串含 `ruleEPassed=` 与 `run-as`；play 与 sideload 包名分别出现在各自命令里。`:feature:debug` 仍无 ORT；`:core:tool:test` 与 `checkChannelLeak` 过。
- [x] AC5 git 无 onnx、无 `eval/intent/predictions.jsonl`；设置页文案不变。
