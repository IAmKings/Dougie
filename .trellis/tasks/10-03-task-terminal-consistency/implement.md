# 任务终态一致性 — 执行计划

## Order

1. **Stage 1 — core/runtime 终态修复**
   - `TaskManager`：`persistIntervalMs` 构造参数、`shouldPersist` + emit 回调节流、`markCancelled` 包 `NonCancellable`。
   - `TaskStore.kt`：`recoverInterrupted` 改全量恢复返回 `List<AgentTask>`；同步更新 `DougieApplication` 调用点。
   - 测试：取消落盘（Fake store upsert 带 `yield()` 挂起点复现原缺陷）、recoverInterrupted 多僵尸、persist 节流（interval 两个极端 + 计数 store）。既有 recoverInterrupted 测试如签名变化同步更新。
   - 验证：`./gradlew :core:runtime:test`。【回滚点 1】

2. **Stage 2 — core/tool 清理**
   - `VerifiedResultJson.kt` 新建；`AppIntentTool`/`SmsComposeTool` 删私有实现改调用。
   - `LoopEngine` toolCallId 常量化 + `MAX_VERIFY_FAILURES` 处补一行跨工具计数语义注释。
   - 验证：`./gradlew :core:tool:test :core:runtime:test`。【回滚点 2】

3. **Stage 3 — bridge 接线测试**
   - `app/src/testSideload/kotlin/com/dougie/app/ChannelToolsBridgeWiringTest.kt`。
   - 验证：`./gradlew :app:testSideloadDebugUnitTest :app:checkChannelLeak`。若源集机制不可行，在 prd.md AC6 记录原因。【回滚点 3】

4. **Stage 4 — 全量**
   - `./gradlew :core:model:test :core:tool:test :core:runtime:test :tool:accessibility:test :app:checkChannelLeak :core:llm:test :app:testSideloadDebugUnitTest`

## Validation

- 各 Stage gradle 命令见上；AC1 的 Fake store 必须先在未修复代码上确认能复现（红→绿），防止测试假阳。
- **红→绿记录**：AC1 已执行红态验证——临时摘除 `withContext(NonCancellable)` 后 `cancelledTaskPersistsTerminalFailedRow` 失败（行停在 THINKING、lastError 为取消前状态），恢复修复后通过；另在红跑中发现并修正了测试自身两处问题（虚拟时间推进触发 LLM_TIMEOUT → 改用 `runCurrent`；缺 `LlmResponse` import）。

## Rollback points

- Stage 1 独立 revert；Stage 2 独立 revert；Stage 3 独立 revert。

## Do not

- 不新增任何依赖；不动冻结契约；不 amend；不 push。
