# E1 复读修复（真机验收缺陷）

## Goal

prompt-budget-guard 方案 A 真机验收（E1 场景）发现缺陷：本地最小档（chat-qwen06 / Qwen3-0.6B）在有工具结果的轮次直接复读用户原话。修复 localPrompt 的排版：不再以裸提问结尾。

## Confirmed facts（真机 + 代码）

- 现状第二轮 prompt 结尾为 `…\n\n用户这次的提问：现在几点？另外明天提醒我开会`——最后一行是裸提问，无任何后续指令或补全提示。
- 0.6B 级模型对 prompt 末尾裸文本有强复读倾向；「不要复述问题」的指令（LOCAL_AFTER_TOOL_RESULTS）位于提问**之前**，对小模型失去近因约束。
- `ChatLlmProvider` 将整段 prompt 作为单条消息经 `sendMessageAsync` 发送（引擎套自己的聊天模板，thinking 关闭）；`stripLeadingQuestion` 在回复恰为原话时原样返回，无法兜底。
- 协议态路径（无工具结果、userBlock=task.input 结尾）是真机已验证的既有行为（冻结契约 14/14），本次不动。

## Requirements

- R1 localPrompt 工具结果轮布局改为：`用户这次的提问：{input}` → 空行 → `工具结果：` → 各条 trace → 空行 → 指令行收尾。提问不再位于 prompt 末尾。
- R2 指令行（LOCAL_AFTER_TOOL_RESULTS）措辞改为「上面已有用户这次的提问和对应的工具结果。…」，仅当 taught 非空时追加在 body 末尾（原在 followUp 中、提问之前）。
- R3 输入为空时保持只给工具结果、指令行仍收尾；协议态与空闲态路径不变。
- R4 既有测试按新布局更新（精确等值断言 :393、contains 断言 :550），并新增「指令行位于提问与结果之后（prompt 不以提问结尾）」断言。
- R5 不动 14 条冻结契约；不动 ChatLlmProvider/stripLeadingQuestion。

## Acceptance Criteria

- [x] AC1 JVM 测试：工具结果 + taught 描述符的 prompt 中，指令行 indexOf 大于提问 indexOf 与结果 indexOf（prompt 不以提问结尾）。
- [x] AC2 JVM 测试：既有精确等值断言更新为新布局且通过；协议态/空闲态路径断言不变仍绿。
- [x] AC3 红态验证：新断言在未修复实现上失败。
- [x] AC4 全量 `:core:llm:test` + `:app:testSideloadDebugUnitTest` + `:app:checkChannelLeak` 绿；真机复测 E1 记回 device-checklist（待复测；红态验证已由临时回退执行：2 项布局断言在旧布局上失败）。

## Out of scope

- 换更大 SKU 复测（属验收方对比手段，不改代码）。
- 协议态首轮布局、stripLeadingQuestion、云端路径。
