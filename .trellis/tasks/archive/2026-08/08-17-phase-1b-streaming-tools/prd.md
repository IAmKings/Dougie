# Phase 1b Streaming + 多 Tool

## Goal

在 Phase 1a 云端电量闭环上，让对话 **流式输出**，并可靠调用 **至少两个 L0 Tool**（电量 + 当前时间），对 LLM Tool Calling 幻觉做 **Sanitize 后再执行**。

## Background

- 依赖：已归档的 `08-17-phase-1a-cloud-battery`。
- 产品：`PRD.md` §15 Phase 1b、§8.4 `Flow<LlmEvent>`、§6.4 ToolCallSanitizer、§6.3 时间/电量为 L0。
- 幂等键形状已在 1a：`taskId + toolCallId`。本阶段要在第二个 Tool 上保持同一契约。

## Requirements

- **R1** `LlmProvider.generate`（或并列的 `stream`）对云端 Provider 使用 OpenAI `stream: true` SSE；Gateway 仍是唯一出境强制点，deny 时不得打开 HTTP。
- **R2** Loop 把文本增量写进 `AgentTask`（如 `streamingText`），Chat 在完成前就能看到逐字/逐段回复；完成后 `finalAnswer` 与气泡一致。取消 `TaskManager` 任务须取消进行中的 HTTP 流。
- **R3** 注册第二个 Tool：`time`（当前时间）。返回 JSON 至少含 ISO 本地时间（或 epoch + zone）。实现放在 JVM `:core:tool`（`java.time`），`:feature:*` 不读系统时钟。
- **R4** `ToolCallSanitizer`：未知 tool 拒绝；非法 JSON 尝试修成 `{}`；数字/布尔以字符串出现时强转；丢弃 schema 外字段；缺省可填默认值。无法修复才 `FAILED` 并给可读错误，不要直接把原始 LLM 参数丢进 Tool。
- **R5** 一次任务中可先后调用 `time` 与 `battery`（顺序由模型决定）。Chat Tool 卡片对非 battery 工具显示通用名称，不要写死「电池」。
- **R6** Phase 0 Fake 三连 battery 测试仍通过。新增 JVM 测试覆盖：SSE 文本增量、流式 tool_call 拼装、Sanitizer 强转、Gateway deny 不发起 stream 请求。

## Acceptance Criteria

- [ ] MockWebServer：SSE 多段 `delta.content` 拼成完整 FinalAnswer。
- [ ] MockWebServer：流式 `tool_calls` 增量能拼出完整 `LlmResponse.ToolCall`。
- [ ] Sanitizer：`"battery_percent":"80"` 这类可修复参数不会导致 Task 失败（用带 schema 的假 Tool 或 time 的空 schema + 多余字段）。
- [ ] 未知 tool 名 → FAILED，用户可见，不执行。
- [ ] `allowCloud=false` 时 stream 路径 requestCount=0。
- [ ] Chat 在 THINKING 且已有 `streamingText` 时展示该文本。
- [ ] `./gradlew :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :app:assembleDebug` 通过。

## Out of scope

- Room / Task History / Process death
- 日历等 L1+ 与 Confirm Card
- 完整 PolicyEngine（权限/审计日志落库）
- Token budget 截断 Memory（尚无 Memory）
- `:cli`

## Constraints

- `:core:*` JVM-only。
- 不静默 Fake。
- 不 log Prompt / Key / SSE 原文。
