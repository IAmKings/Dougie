# 复读兜底与承诺诚实地板（E1 五测）

## Goal

五测两个残留问题：①0.6B 调完时间工具后复读用户原话（提示词排版已正确仍复现——小模型无视指令，需决策层兜底）；②MiniCPM-2B 回答时间后用「会说提醒我」的文字承诺替代 calendar_create 调用——声称执行了未执行的操作，属验证层要消灭的幻觉类别。

## Confirmed facts

- 0.6B 本轮正常闭合了时间 JSON 并执行（截断修复未触发、协议态工作正常），复读发生在工具结果轮；`stripLeadingQuestion` 对「回复恰为原话」返回原文，无法兜底。
- 2B 在宽松措辞下选择了文字分支：口头承诺提醒而未调用 calendar_create。当前指令「若还需要调用工具…就输出 JSON；否则直接回答」给了「文字承诺」这条不诚实的捷径。
- 工具结果轮指令（LOCAL_AFTER_TOOL_RESULTS）是唯一同时到达两个模型的杠杆；0.6B 对指令不敏感，2B 敏感——指令收紧主要利 2B，0.6B 靠决策层兜底。
- `UserFacingErrors.LLM_EMPTY_REPLY = "模型没有给出回复，请重试。"` 语义与「复读=无有效回答」精确匹配，复用既有文案。

## Requirements

- R1 `decideLocalReply` 的所有 TextDelta 出口加复读兜底：回复（剥离「用户这次的提问：」前缀后）与 task.input 归一化（仅保留字母/数字，含 CJK）相等 → 返回 `TextDelta(LLM_EMPTY_REPLY)`，绝不把原话当回答显示。
- R2 `LOCAL_AFTER_TOOL_RESULTS` 收紧：提问中还需执行的部分（如建日程、打开应用）必须输出一行工具 JSON 去完成，不要只用文字承诺会做；其余部分用一两句中文直接回答，不要复述问题（保持以「不要复述问题。」结尾）。
- R3 复读兜底只做归一化全等比较，不碰部分引用（正文中引用问题属正常回答）。
- R4 不动 14 条冻结契约、LoopEngine、云端路径；无新增日志。

## Acceptance Criteria

- [ ] AC1 JVM 测试：工具结果轮回复与原话全等（含带「用户这次的提问：」前缀形态）→ TextDelta(LLM_EMPTY_REPLY)；正常回答（含引用问题的长回答）不受影响。
- [ ] AC2 JVM 测试：新指令文案在 prompt 中（含「不要只用文字承诺」），以「不要复述问题。」结尾；红态：摘除复读兜底后 AC1 失败。
- [ ] AC3 `:core:llm:test` + `:app:testSideloadDebugUnitTest` + `:app:checkChannelLeak` 绿；冻结契约不动。
- [ ] AC4 真机六测：0.6B 不再把原话当回答；2B 建日程走确认卡（args 无效时诚实报错也算过）；结果记回 device-checklist。

## Out of scope

- 为 calendar_create 注入当前日期上下文与 startIso 格式教学（若六测 2B 因参数无效失败，另立任务）。
- 0.6B 复读的模型层治理（换 SKU/微调）。
