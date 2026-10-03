# 任务终态一致性

## Goal

修复深度审查发现的三个 P0 终态一致性问题——取消任务终态写不进库（僵尸行）、崩溃恢复只救最新一条、流式回答逐 delta 全量持久化写放大——并顺手清掉验证层的三个小额技术债（verified JSON helper 重复、toolCallId 魔法字符串、bridge 无接线测试）。

## Confirmed facts

- `TaskManager.kt:82-84` 在已取消的协程内调 `markCancelled()`，其 `persist()`/`reloadTranscript()` 会被 `CancellationException` 立即打断：UI 显示已取消，但 DB 行停在非终态（已亲自核实 `markCancelled.kt:207-219` 与 `persist` 的 `withContext(Dispatchers.IO)` 链路）。
- `TaskStore.kt:127-139` `recoverInterrupted` 只检查 `listRecent(1)`：僵尸行被更新任务顶下去后永不恢复。审查代理推测的「启动 isBusy 恒真死锁」核实后不成立（最新僵尸会被恢复），真实缺口是**多条**僵尸只救最新。
- `TaskManager.kt:77-81` 的 emit 回调对每个 `TextDelta` 全量 `TaskSnapshotCodec.encode` + SQLite `upsert`；一次数百 delta 的回答即数百次全量重写。
- `SqliteTaskStore.listRecent` 按 `updated_at DESC` 排序；`agent_tasks` 表无 status 列，SQL 无法按状态过滤，恢复需取全量后内存过滤（一次性启动成本，可接受）。
- `recoverInterrupted` 唯一调用方是 `DougieApplication.kt:316`（runBlocking 启动恢复，seed 当前会话的恢复行）。
- `withVerified`/`verifiedFromJson` 在 `AppIntentTool.kt:96/105` 与 `SmsComposeTool.kt:87/96` 逐字重复。
- app 模块无 mockito/mockk/robolectric；`ContextWrapper(null)` 可作为 JVM 单测中不被触碰的 Context（`scriptPrivileged=false` 时 `ChannelTools.register` 不调用 context 任何方法）；ChannelTools 在双 flavor 各有一份，接线测试必须放 `src/testSideload` 变体专属测试源集，否则 play 变体编译失败。

## Requirements

- R1 `markCancelled` 的 `persist`/`reloadTranscript` 包 `withContext(NonCancellable)`：取消后 DB 行必须为终态 `FAILED` 且 `lastError=CANCELLED`。
- R2 `recoverInterrupted` 改为取全量、把**所有**非终态行翻成 `FAILED(INTERRUPTED)` 并返回列表（签名 `AgentTask?` → `List<AgentTask>`）；调用方 seed 当前会话的最近一条恢复行。
- R3 persist 节流：emit 回调里非终态状态至多每 `persistIntervalMs`（构造参数，默认 250ms）落盘一次；**终态与首个 emit 必落盘**；`_task` UI 更新与终态落盘时机不变；`markCancelled` 路径不受节流影响。
- R4 `withVerified`/`verifiedFromJson` 提取为 core/tool 共享 helper，`AppIntentTool`/`SmsComposeTool` 复用，对外行为逐字节不变。
- R5 LoopEngine 的 `match-then-tap-1/2`、`intent-route-1`、`intent-route-retry-1` 提为 companion 常量。
- R6 新增 sideload 变体专属接线测试：`ChannelTools.register`（consent=true）后 `ForegroundProbeBridge.provider` 与 `TreeProbeBridge.provider` 均 非 null；consent=false 时不填充。
- R7 验证预算按 trace 全局计数（跨工具）的既有语义保持不变，在 `MAX_VERIFY_FAILURES` 处加一行注释明确该语义。

## Acceptance Criteria

- [x] AC1 JVM 测试：用 upsert 带挂起点的 Fake store（复现原缺陷形状），submit 后 cancel，断言 store 中该任务 `FAILED` 且 `lastError=UserFacingErrors.CANCELLED`。
- [x] AC2 JVM 测试：多条非终态行（含被完成行顶下的旧僵尸）→ `recoverInterrupted` 全部翻 FAILED 并返回；全终态/空库 → 返回空列表。
- [x] AC3 JVM 测试：`persistIntervalMs=Long.MAX_VALUE` 时 N 条 TextDelta 只落盘常数次且终态必落盘；`persistIntervalMs=0` 时每次 emit 落盘（旧行为）。
- [x] AC4 现有全部测试不动且保持绿（含 `ConversationTaskManagerTest`、`TaskStoreTest` 中既有 recoverInterrupted 用例如签名变化需同步更新）。
- [x] AC5 全量 `./gradlew :core:model:test :core:tool:test :core:runtime:test :tool:accessibility:test :app:checkChannelLeak :core:llm:test :app:testSideloadDebugUnitTest` 通过。
- [x] AC6 R6 的接线测试在 `:app:testSideloadDebugUnitTest` 中执行且通过（若源集机制不可行，在本文件记录原因与替代方案后才允许降级）。

## Out of scope

- `agent_tasks` 表加 status/conversation_id 列与索引（另一数据层任务）。
- idempotency/audit TTL 清理（另一数据保留任务）。
- prompt 预算与注入防护（另一 LLM 上下文任务）。
- LoopEngine 拆分重构。
