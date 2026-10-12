# 本地多步工具轮放行（E1 复测缺陷）

## Goal

E1 真机复测：0.6B 在工具结果轮输出 calendar_create JSON（语义正确地想处理「提醒开会」），但被当文本显示给用户；MiniCPM-2B 用文字回应通过。修复：本地路径中，工具结果轮解析出的工具调用若命中本地教学清单，照常作为 ToolCall 事件交给 LoopEngine（sanitizer/确认门/幂等/预算全部既有把关），实现多步任务闭环；未教学工具名维持现行为。

## Confirmed facts

- `ChatLlmProvider.onDone` 的分支序：repeat→「已获得工具结果。」；`tool != null && localToolProtocolActive`→发 ToolCall；否则 TextDelta 原文。第二轮 protocolActive=false（有 resultJson），JSON 落入 TextDelta → 用户看到原始 JSON。
- 0.6B 若输出的是 time 重复调用会被 repeat 守卫拦成文本，故用户看到的 JSON 是别的工具（与 E1 语义吻合，calendar_create）。
- LoopEngine 侧对本地 ToolCall 的把关完备：sanitizer（UNKNOWN_TOOL/INVALID_TOOL_ARGS）、PolicyEngine（calendar_create=L2 确认卡）、幂等、maxLoops=8、repeat 守卫。
- 14 条冻结契约全部为首轮（protocolActive）路径，本轮改动不触及；`ChatPromptAssembler.localTeachable` 是现成的教学名单来源（tap_swipe/js/py/sms_compose 天然不在名单）。
- 直通风险点：未教学但恰好 parse 成 JSON 的文本若放行会以 UNKNOWN_TOOL 判死任务——因此放行以教学名单为闸。

## Requirements

- R1 core/llm 新增可测助手 `localAfterToolToolCallAllowed(descriptors, call)`：call.name 在 localTeachable 名单内 → true；descriptors 为空 → false。
- R2 `ChatLlmProvider.onDone` 的 ToolCall 分支条件改为 `protocolActive || localAfterToolToolCallAllowed`（repeat 守卫仍最优先）。
- R3 `LOCAL_AFTER_TOOL_RESULTS` 措辞改为允许按需再调用：若还需工具才能完整回答就输出一行工具 JSON，否则直接中文回答、不复述。
- R4 未教学工具名的 JSON 与纯文本路径行为不变（TextDelta 原文，既有兜底）。
- R5 不动 14 条冻结契约；不动 LoopEngine/PolicyEngine；无新增日志。

## Acceptance Criteria

- [ ] AC1 JVM 测试：taught 工具（calendar_create）→ allowed；未教学（tap_swipe/sms_compose/未知名）→ false；descriptors 空 → false。
- [ ] AC2 JVM 测试：更新后的 after-tool prompt 断言（新措辞、收尾位置不变）全绿。
- [ ] AC3 `:core:llm:test` + `:app:testSideloadDebugUnitTest` + `:app:checkChannelLeak` + `:app:compileSideloadDebugKotlin` 绿；冻结契约测试不动全绿。
- [ ] AC4 真机复测 E1（0.6B）：时间被回答，「提醒开会」触发 calendar_create 确认卡，确认后日程创建、终答覆盖两件事；结果记回 device-checklist。

## Out of scope

- 未教学工具的二轮放行；轮数预算调整（maxLoops=8 既有）；云端路径；协议态首轮布局。
