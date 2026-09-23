# 端侧工具契约 — 设计

## Boundary

计分与冻结集在 `:core:llm`，不引用 Android，不调用 LiteRT。真机生成留在侧载已有的 `ChatLlmProvider` 路径上，由 `:app` 开发者页触发。Play 没有本地引擎时，入口不发起推理。

## Frozen file

`core/llm/src/main/resources/eval/local-tool-contract.jsonl`

```json
{"id":"time-1","text":"现在几点","expectTool":"time"}
{"id":"chat-1","text":"你好","expectTool":null}
```

`expectTool` 只用：`time`、`battery`、`calendar_query`、`location`、`clipboard_read`、`app_intent` 或 `null`。说法用日常中文，避免把工具英文名写进用户句子。

## Scorer

`LocalToolContractEval`（`:core:llm`）：

- `load(stream)` 解析冻结集。重复 id、缺字段、未知 `expectTool` 在加载时失败。
- `score(items, predicted: Map<String, String?>)`：缺键或空字符串视为未调用。与 `expectTool` 全等才算对（null 对 null）。
- `passed` 当且仅当 `items.size == 14` 且 `correct == 14`。
- `toString()` 形如 `local-tool n=14 correct=14 passed=true`。不要 `text`，不要工具名。

预测文件（仅设备，不入库）：`files/eval/local-tool/predictions.jsonl`，行含 `id` 与 `predictedTool`。调试页可以告诉用户相对路径和 `adb exec-out run-as`，不把 jsonl 正文贴上屏幕。

## Device run

对每一条：

1. 用现有 `localPrompt`（当前常驻规则 lambda 一并生效；试跑不关闭用户规则，报告不引用规则正文）。
2. 一次非流式端侧生成。取消沿用现有协程取消，不记成契约失败。
3. `LocalToolCallParser` 取出第一个工具名；解析不到则为 null。
4. 写预测行。

只接受 `activeChatSku == chat-qwen06` 且该档文件存在。其它档按钮禁用。权重不在时不写一份「通过」的报告。

## If the model misses

允许改 `LOCAL_TOOL_ASK_NEEDLES` 与 `localToolProtocol` 里已有工具的例句，让这 14 条的协议开关和示例与期望一致。闲聊两条必须仍然不打开工具协议（`localToolProtocolActive == false`），这样模型没有工具 JSON 可抄。JVM 测试锁住：`你好` 与 `你是谁` 的 `localToolProtocolActive` 为 false；六类正例为 true。

不得把新工具名加进 `LOCAL_TEACH_NAMES`。不得为了过门换 SKU。

## Logging

与规则 E 相同：Logcat、审计、调试页正文都不含用例 `text` 或模型回复。`toString()` 只有计数。
