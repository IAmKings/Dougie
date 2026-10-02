# 动作验证层 — 执行计划

## Order

1. **Stage 2 — core/model 契约**
   - `AgentTask.kt`：`ToolResult` 与 `ToolTraceEntry` 加 `val verified: Boolean? = null`。
   - `AgentException.kt`：`UserFacingErrors` 加 `VERIFY_FAILED = "已执行操作，但未能确认是否成功。"`。
   - grep data 层 `ToolTraceEntry` 手工映射（如有则同步补列）。
   - 验证：`./gradlew :core:model:test :core:tool:test :core:runtime:test`（编译 + 既有测试回归）。

2. **Stage 3 — core/tool 观察与验证**
   - 新建 `ForegroundProbePort.kt`（fun interface + `FakeForegroundProbe`，可脚本化每次返回值）。
   - 新建 `ForegroundVerifier.kt`（suspend 轮询；首查立即，未命中 `delay(pollMs)` 循环至 `timeoutMs`）。
   - `AppIntentTool`：构造器加 `foregroundProbe: ForegroundProbePort? = null`、`verifyTimeoutMs: Long = 5_000`、`verifyPollMs: Long = 250`；按 design 数据流接入；JSON 注入用 kotlinx parse+put，仅非 null 时加键。
   - 测试（`AppIntentToolTest` + 新 `ForegroundVerifierTest`，覆盖 AC1/AC2/AC3/AC5 的工具侧、AC6）。
   - 验证：`./gradlew :core:tool:test`。【回滚点 1】

3. **Stage 4 — core/runtime FAIL 分支**
   - `LoopEngine`：`ToolPass.Success` 加 `verified`；trace 写入 verified；主循环预算分支；`completeFromNamedShortcut` 重试分支。
   - 测试（`LoopEngineTest`：AC2 的循环侧、AC4 捷径重试、AC5 回灌、AC3 字节一致）。
   - 验证：`./gradlew :core:runtime:test`。【回滚点 2】

4. **Stage 5 — app 接线**
   - app main：`ForegroundProbeBridge`；`DougieApplication` 传探针 lambda。
   - `tool/accessibility`：`AccessibilityForegroundProbe(gesturePort)`。
   - sideload `ChannelTools`：复用同一 `AndroidGesturePort` 填 bridge。
   - 验证：`./gradlew :app:checkChannelLeak :tool:accessibility:test`。【回滚点 3】

5. **Stage 6 — 全量**
   - `./gradlew :core:model:test :core:tool:test :core:runtime:test :tool:accessibility:test :app:checkChannelLeak`（AC7）。

## Validation

- 每个 Stage 的 gradle 命令见上；最终 AC7 全量。
- 真机行为验证（sideload：断网无障碍开微信观察 verified=true；杀进程场景观察重试与如实文案）不进 CI，记录到任务笔记。

## Rollback points

- Stage 3 前 revert model commit；Stage 4 前 revert tool commit；Stage 5 前 revert runtime commit；app 接线单独 revert 即恢复 Play 现状。

## Do not

- 不打印任务输入句子与用户内容到日志；`AuditLog` 只记 SUCCESS/FAILED（验证失败记 FAILED）。
- 不动 `.trellis/spec/` 之外的冻结文件；不 amend；不 push。
