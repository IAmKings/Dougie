# 本地回复决策纯函数化（E1 三测缺陷）

## Goal

真机三测：MiniCPM-2B 在 E1 中返回原始 JSON `{"name":"calendar_query","args":{}}` 给用户。按 10-12-round2-tool-exec 的门控，裸的教学名单内 JSON 应被放行执行而非显示——故实际输出大概率不是裸 JSON（带前后缀文字导致整段解析失败，落入「当文本显示」兜底）。修复：把 ChatLlmProvider 的回复→事件决策整体收进 core/llm 纯函数，封死「原始 JSON 显示给用户」这一类缺口，并使决策逻辑全 JVM 可测。

## Confirmed facts

- `calendar_query` 在 `LOCAL_TEACH_NAMES` 内；当前门控下裸 `{"name":"calendar_query","args":{}}` 在任意轮次都会被放行执行，不会显示。显示原文的唯一代码路径是整段解析失败（前后缀文字、全角字符等）落 `TextDelta(原文)`。
- 复读风险：模型反复输出同名工具但 args 每次不同可绕过 repeat 守卫（精确/结构化比较只挡完全同参），在 maxLoops=8 内来回执行；repeat 命中时终答「已获得工具结果。」可接受（既有行为）。
- 注入面评估：夹在文本中的 JSON 若提取放行，来源可能是模型复述的工具结果/剪贴板内容；教学名单 12 工具均为 L0-L2，状态变更类（calendar_create/app_intent/screen_capture）必经确认卡，sms_compose/tap_swipe 不在名单——人审闸门保留，可接受。
- `LocalToolCallParser` 的「混合中文+JSON 保持文本」契约是解析器层的既有测试，本次不动解析器，提取逻辑放在新决策函数层。

## Requirements

- R1 core/llm 新增 `ChatPromptAssembler.decideLocalReply(task, descriptors, reply): LlmEvent?`，统一四条路径：①整段可解析→复读守卫→(protocolActive‖教学名单)放行→否则优雅文案；②整段不可解析但含「可解析且带 name 字段的 JSON 对象」→提取后走同样分类，未放行时若 JSON 外仍有文字则显示剩余文字，否则优雅文案；③纯文本→stripLeadingQuestion 后显示；④空回复→null。
- R2 永不把含 `"name":` 的原始工具 JSON 显示给用户：未放行的调用显示 `（该操作暂不支持自动执行，已忽略。）` 或剥离 JSON 后的剩余文字。
- R3 `ChatLlmProvider.onDone` 改为单点调用 decideLocalReply；`localAfterToolToolCallAllowed` 保留被复用。
- R4 不动 LocalToolCallParser 及其「混合中文+JSON 保持文本」测试；不动 14 条冻结契约；不改 LoopEngine/云端路径；无新增日志。

## Acceptance Criteria

- [x] AC1 JVM 测试（用三测的实际载荷）：工具结果轮 + `{"name":"calendar_query","args":{}}` → 发出 ToolCall（钉住门控）。
- [x] AC2 JVM 测试：前后缀文字夹带同款 JSON → 仍发出 ToolCall；夹带未教学 JSON（tap_swipe）→ TextDelta 且不含 `"name"` 原文（含剩余文字或优雅文案）。
- [x] AC3 JVM 测试：整段未教学 JSON → 优雅文案不显示原文；复读（含结构化同参）→「已获得工具结果。」；纯文本走 stripLeadingQuestion；protocolActive 首轮兼容。
- [x] AC4 `:core:llm:test` + `:app:compileSideloadDebugKotlin` + `:app:testSideloadDebugUnitTest` + `:app:checkChannelLeak` 绿；冻结契约测试不动全绿。
- [x] AC5 真机四测 E1（0.6B 与 2B 都测）：任何情况下气泡里不出现原始工具 JSON；结果记回 device-checklist（AC5 待真机四测；红态：提取逻辑回退后 2 项夹带测试失败）

## Follow-up（四测后追加，同任务闭环）

真机四测仍显示原始 JSON；拉取设备 DB 取证：finalAnswer 为**截断的多行 JSON**（`toolTrace=[]`，首轮断流，缺外层收尾括号），整段解析与完整对象提取都接不住。追加 R6：`repairTruncatedJsonObject`——以 `{` 开头且含 `"name"` 的不平衡文本按括号深度补齐收尾（必要时先补引号、去尾逗号）重解析，成功则按既定分类（复读/放行/优雅文案），失败给优雅文案；永不显示原文。AC 追加：

- [x] AC6 真机精确载荷（截断多行 calendar_query JSON）→ 放行 ToolCall；截断复读 → 已获得工具结果；截断未教学/不可修复 → 优雅文案（4 项测试，红态：摘除修复分支后 3 项失败）。

## Out of scope

- 「已获得工具结果。」作为复读终答的措辞优化；args 渐变的同名工具重复执行限流（maxLoops 兜底）；云端路径。
