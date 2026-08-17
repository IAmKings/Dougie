# Phase 4 Task Recovery Reliability

## Goal

进程被杀后 **任务状态可恢复、可重新提交**；创建型 Tool **跨进程不重复副作用**；失败有自然语言错误和重试；有 Task History 与脱敏 Audit。**不恢复 LLM 流断点。**

## Background

- 依赖：已归档 Phase 3b。
- 产品：`PRD.md` §15 Phase 4、§16.5、Case 06–09、§11.3 Task History。
- 视觉：`design/Agent与工具/Agent_Tasks_-_History__Status__*.html`。Dougie 文案。

## Requirements

- **R1** `TaskStore`（JVM 接口）：每次 Loop `emit` 持久化 `AgentTask`。Android SQLite 实现；JVM 用内存实现测恢复。
- **R2** 启动时若最近任务非 COMPLETED/FAILED：标记 `FAILED` +「任务已中断，请重新提交。」写入 store，Chat 能看到该错误。不自动续跑 LLM。
- **R3** `IdempotencyStore` 持久化 `idempotencyKey → resultJson`。`calendar_create` 使用它替代进程内 Map。同一 key 二次 execute 不调用 `CalendarPort.createEvent`。
- **R4** 网络类 LLM 失败最多再试 2 次（仅 `NETWORK_FAILED` / IOException），再失败走现有用户文案。
- **R5** Chat FAILED 显示重试（同一 input `submit`）。`inputEnabled` 在 FAILED 为 true。
- **R6** `:feature:history`：最近任务列表（输入摘要、状态、loopCount、tool 名链、错误）。Chat 底栏「任务」进入。
- **R7** `AuditLog`：toolName + taskId + outcome + time。禁止写 Prompt、Key、日历正文、剪贴板、坐标、截图像素。

## Acceptance Criteria

- [ ] JVM：upsert 非终态任务，模拟重启 `recover()` 后 status=FAILED 且 lastError 为中断文案。
- [ ] JVM：calendar_create 相同 key 两次，port.create 只调一次（store 跨「新 Tool 实例」仍命中）。
- [ ] JVM：Provider 连续 IO 失败时有重试（MockWebServer 或 fake 计数 ≥3 次调用后失败）。
- [ ] ChatUiState FAILED 可 retry；History 列出已持久化任务。
- [ ] `./gradlew :core:runtime:test :core:tool:test :core:llm:test :app:assembleDebug` 通过。

## Out of scope

- LLM 流续传
- WorkManager 长后台 Agent
- 完整加密 Audit 导出
- App Intent 新 Tool
- Play/Sideload flavors

## Constraints

- `:core:*` JVM-only。
- 不静默 Fake LLM。
- 恢复路径不得再次创建日历事件。
