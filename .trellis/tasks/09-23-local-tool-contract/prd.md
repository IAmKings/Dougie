# 端侧工具契约

## Goal

用一份冻结的中文用例规定最小档端侧模型（`chat-qwen06`）该调用哪个工具，或该保持闲聊。开发者页能对已安装的这一档重跑，并用只含计数的报告说明过没过。

## User value

「现在几点」「电量多少」「你好」这类日常说法有一份不会随手改掉的预期。模型退步时能在开发者页看出来，而不是靠再写一条关键词碰运气。

## Confirmed facts

- 最小档 id 是 `ChatModelLayout.ID` = `chat-qwen06`。更大的是 `chat-minicpm1b`、`chat-minicpm2b`。
- 端侧只有 `looksLikeLocalToolAsk` 为真时才附上工具 JSON 协议（`ChatPromptAssembler.localToolProtocolActive`）。解析在 `LocalToolCallParser`。
- 规则 E / Kokoro 的开发者页报告 `toString()` 只有计数和比率，正文在 `filesDir` 的 jsonl，并用 `adb exec-out run-as` 提示拉取。本契约沿用这条，不把说法打进 Logcat。

## Requirements

- R1 冻结用例放在 `:core:llm` 主资源 `eval/local-tool-contract.jsonl`。每行 `id`、`text`、`expectTool`。`expectTool` 为工具名或 JSON `null`（闲聊）。
- R2 至少覆盖：`time`、`battery`、`calendar_query`、`location`、`clipboard_read`、`app_intent` 各 2 条；闲聊 `你好`、`你是谁` 各 1 条且 `expectTool` 为 null。一共 14 条。不增加工具。
- R3 纯 JVM 计分器：给定 id → 预测工具名（或空），对冻结集算对错。`toString()` 只有条数、正确数、是否通过，没有说法、没有工具名。14 条全对才算通过。缺预测算错。
- R4 JVM 测试用罐头预测证明计分；另测冻结文件的 id 与期望工具名集合。CI 不加载 LiteRT。
- R5 开发者页在 `chat-qwen06` 已安装且为当前档时提供一次试跑：逐条走现有端侧生成与 `LocalToolCallParser`，把预测写到应用私有 `eval/local-tool/predictions.jsonl`。页面只显示计数报告、相对路径，以及 `adb exec-out run-as <package> cat files/eval/local-tool/predictions.jsonl`。模型缺失时用现有「去下载 / 未就绪」一类中文错误，不假装通过。
- R6 当前档不是 `chat-qwen06` 时，按钮不可用，并说明契约只认这一档。
- R7 若试跑不通过，只允许调整现有关键词表或协议例句，使这 14 条能走对；不得新增工具、不得改成更大模型、不得把闲聊判成工具。改完必须能在开发者页重跑。真机不过就停，把失败计数留在任务记录里，不扩大范围。

## Acceptance Criteria

- [ ] AC1 资源文件 14 行，六个工具各 2 条，两条闲聊期望为空。
- [ ] AC2 罐头「全对」报告通过；故意错一条则不通过；`toString()` 不含用例原文。
- [ ] AC3 开发者页试跑不把说法或模型原文写入 Logcat；界面没有「已达标」字样，通过与否只看计数报告里的 passed 字段含义（实现用计数句子表达）。
- [ ] AC4 Play 包不因此加入对话权重或 `:tool:chatllm`。试跑入口若只在侧载调试页能点，Play 上该按钮保持不可用或隐藏，且 `checkChannelLeak` 通过。
- [ ] AC5 工具注册表名称集合与改动前一致。

## Out of scope

- 在 CI 里跑真模型。
- 给 1B/2B 定第二套门槛。
- 覆盖写日历、写剪贴板、截屏、朗读以外的本阶段必测项（它们留在现有教学名单里，但不进这 14 条）。
- 规则 D、Kokoro。
