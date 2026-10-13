# 剪贴板方向纠正（E2 七测取证）

## Goal

七测取证（设备 DB）：「帮我复制"你好"」任务执行的是 `clipboard_read`（读空剪贴板）而非 `clipboard_write`——2B 把写入意图调成了读取。修复：输入带引号包裹的载荷时，模型发出的 clipboard_read 确定性重映射为 clipboard_write（文本=引号内容）；空文本写入永不执行。

## Confirmed facts

- 设备 DB（2026-10-13）：「帮我复制"你好"」任务 toolTrace=[clipboard_read, resultJson={"ok":true,"text":""}]，status=COMPLETED——读空剪贴板后完成，从未写入。
- 输入中的引号载荷是写入意图的确定性证据（与确定性捷径路径 `IntentRouteAnswers.parseClipboardWrite` 的 firstQuoted 提取同规则）；用户实际使用弯引号（“”），firstQuoted 现只支持「」『』"/'，本任务的新助手需覆盖弯引号。
- 声明门已在真机生效（同批 E1 任务回答末尾带「（注意：我还没有真正创建这个日程或提醒。）」）；「刚才复制了什么」经复制触发词正常进入协议并执行 clipboard_read。
- clipboard_write 为 L2：重映射后的写入必经确认卡，人审把关。

## Requirements

- R1 core/llm 新增 `firstQuotedText(input)`：支持「」『』直引号"'与弯引号“”‘’的成对提取，取第一个非空内容。
- R2 `classifyLocalCall` 前加方向纠正：call 为 clipboard_read 且输入有引号载荷 → 重映射为 clipboard_write（args.text=载荷）；重映射后或直接到达的 clipboard_write 若文本为空且输入无引号载荷 → 不执行，给优雅文案（空写入无意义）。
- R3 其余工具方向不纠正；不重映射已带非空 text 的 clipboard_write；L2 确认门与幂等不变。
- R4 不动 14 条冻结契约、IntentRouteAnswers（其 firstQuoted 弯引号扩展另立）、LoopEngine；无新增日志。

## Acceptance Criteria

- [ ] AC1 JVM 测试：输入含弯引号载荷 + 模型发 clipboard_read → 放行 ToolCall(clipboard_write, text=载荷)（直引号/「」同）。
- [ ] AC2 JVM 测试：无引号输入的 clipboard_read 保持原样；空文本 clipboard_write 且无引号载荷 → 优雅文案不执行。
- [ ] AC3 红态：AC1/AC2 在未修复实现上失败。
- [ ] AC4 `:core:llm:test` + `:app:testSideloadDebugUnitTest` + `:app:checkChannelLeak` 绿；冻结契约不动。
- [ ] AC5 真机八测 E2：复制→确认卡（显示你好）→ 确认 → 追问读到「你好」；记回 device-checklist。

## Out of scope

- IntentRouteAnswers.firstQuoted 的弯引号扩展（捷径路径，另立）；「工具 JSON 已提供，无需额外操作。」类空读回答的确定性模板化。
