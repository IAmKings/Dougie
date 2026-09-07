# Design

`:core:llm` 增加可测过滤：`ChatPromptAssembler.localTeachable(descriptors)` 只保留 name ∈ `{time, battery, clipboard_read}`，顺序按该集合。`localPrompt` 用过滤后清单 + 三行 JSON 示例；`systemPrefix`（远程）仍用调用方传入的全表。

`ChatLlmProvider.promptFor` 继续把 **全表** 传入 `localPrompt`；过滤发生在 assembler 内，避免 Android 再维护一份名单。Loop `tools` 不变。

`LOCAL_TOOL_PROTOCOL` 扩成三个例子，跟轮 `LOCAL_AFTER_TOOL_RESULTS` 不变。

`LocalToolCallParser`：在整段 `{...}` 失败时，允许剥一层 markdown 围栏后再 parse；**不**从「中文 + 同行 JSON」里抽取（上一刀刻意整段匹配，避免半句话误触发）。若真机 AC 仍因模型在 JSON 前后加字而失败，再开刀放宽，不在本刀赌抽取。

## Rollback

还原 assembler 过滤与协议文案；Provider 接线不变。
