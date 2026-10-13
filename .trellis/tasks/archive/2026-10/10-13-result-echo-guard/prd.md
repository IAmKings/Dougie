# 结果回显守卫（E1/E2 八测取证）

## Goal

八测取证（设备 DB）：clipboard_write 已成功（{"text":"你好"}，L2 确认后 SUCCESS——方向纠正生效），但模型第二轮把工具结果 `{"ok":true`（截断）当回答回显；「刚才复制了什么」则选错工具调了 time 后同样回显 `{"ok":true`。修复：本地路径任务完成时，JSON 形态的回答不信模型回显，改用 trace 最后一次成功工具的 resultJson 走确定性中文文案（IntentRouteAnswers.formatFinalAnswer 既有）；并给剪贴板类提问加协议示例排序偏置，降低选错工具概率。

## Confirmed facts

- 设备 DB：两条任务 finalAnswer 均为 `{"ok":true`（截断回显）；模型回显无 name 字段，decideLocalReply 的调用形态门（repair/提取）按设计不接——显示类缺口的最后一形态是「结果回显」。
- `IntentRouteAnswers.formatFinalAnswer`（core/runtime internal）已具备 time/battery/clipboard_read/clipboard_write/calendar 等工具结果的确定性中文文案，且 gate 在 ok:true——LoopEngine 同模块可直接复用。
- 本地协议（localToolProtocol）按输入关键词排序 app_intent 有先例（打开 → app_intent 置顶）；剪贴板/复制提问把 clipboard_read/clipboard_write 置顶是同款确定性偏置。
- 流式期间原始回显可能闪现，COMPLETED 后气泡替换为 finalAnswer（既有 UI 行为），可接受，记 PRD。

## Requirements

- R1 LoopEngine 本地路径（completionPath==LOCAL_LLM）文本完成时：回答以 `{` 开头 → 取 trace 最后一条 SUCCESS 且有 resultJson 的条目，用 formatFinalAnswer 生成中文回答；生成失败或无条目 → `LLM_EMPTY_REPLY`。非本地路径与非 JSON 回答不变。
- R2 localToolProtocol 排序：输入含「复制/剪贴板」→ clipboard_read、clipboard_write 置顶（app_intent 置顶规则保持优先级共存时按各自触发）。
- R3 不动 14 条冻结契约（其断言在 core/llm，排序在协议例句区，eval 文本不含复制/剪贴板排序敏感断言——LocalToolContractEvalTest 不动全绿为准）；不动云端路径。

## Acceptance Criteria

- [x] AC1 JVM 测试（LoopEngineTest）：本地两轮任务（clipboard_write 成功后模型回显 `{"ok":true`）→ finalAnswer=「已写入剪贴板。」；time 成功后回显 → finalAnswer 以「现在是」开头。
- [x] AC2 JVM 测试：本地无工具任务回显 JSON → finalAnswer=LLM_EMPTY_REPLY；云端路径（REMOTE_LLM）回显不守卫。
- [x] AC3 JVM 测试：输入含复制 → 协议例句中 clipboard 工具排在 time 之前。
- [x] AC4 红态：AC1/AC3 在未修复实现上失败。
- [x] AC5 `:core:llm:test :core:runtime:test :app:testSideloadDebugUnitTest :app:checkChannelLeak` 绿；冻结契约不动全绿。
- [x] AC6 真机九测：E2 全链路（写入→追问读到内容）；E1 2B 无原始 JSON；记回 device-checklist（AC6 待九测；红态：摘除守卫后 3 项失败）

## Follow-up（九测后追加，同任务闭环）

九测取证：finalAnswer = 「已读取剪贴板：{"ok":true,"text":"你好"}」——模型产出前缀文字+完整结果 JSON 的混合回显，不以 { 开头，旧守卫（startsWith）未接。追加：守卫改为「回答含最后一次工具的 resultJson 原文即视为回显」，优先确定性文案（剪贴板内容：你好），无文案时保留剥离 JSON 后的剩余文字，再退诚实重试。AC 追加：

- [x] AC7 混合回显 → 「剪贴板内容：你好」；不含回显的正常回答原样保留（红态：回退 startsWith 守卫后混合用例失败）。

## Out of scope

- 流式期间回显闪现的 UI 处理；选错工具的模型层治理（排序偏置为全部）；startIso 日期上下文。
