# 数据与输入卫生

## Goal

修复深度审查发现的 P1 数据与输入问题：`idempotency` 表只进不出（每次成功工具调用永久加一行）、任务删除不级联清理幂等行、`audit_log` 无上限、`ToolCallSanitizer` 对 STRING 无长度上限 / 对 OBJECT 无深度与大小限制（LLM 可灌 1MB 文本进参数）、`LocalToolCallParser.isRepeatOfSuccessfulCall` 用字符串全等比较导致重复调用防护对键序/空白差异形同虚设。

## Confirmed facts

- `audit_log` 表**已有** `created_at` 列（`SqliteAuditLog.record` 写入），只缺清理；`idempotency` 表只有 key+result_json 两列，无时间戳，加列需 DB 版本迁移。
- `TaskDbHelper.DB_VERSION = 1`，`onUpgrade` 当前对三张表 DROP——本次迁移必须改为增量（ALTER），否则升级用户丢全部任务历史。
- 幂等键 = `taskId + toolCallId`；任务 id 为标准 UUID（36 字符，纯 hex+连字符），`LIKE 'taskId%'` 前缀匹配安全。
- `deleteByConversation` 已在事务内收集 taskId 列表再删 `agent_tasks`，级联删除幂等行挂同一事务即可；`deleteByTaskId` 目前无事务。
- data/tasks 模块**无测试源集**：SQL 行为无法 JVM 测试，以编译 + `checkChannelLeak` + 真机走查覆盖；PRD 显式记录此限制。
- ~~`isRepeatOfSuccessfulCall` 主源码无调用方~~（勘误，检查代理发现）：`ChatLlmProvider.kt:84`（sideload 本地模型路径 onDone）实际调用它——结构化比较是活行为，效果是键序不同的重复本地调用会被正确短路，方向即修复意图；kotlinx `JsonElement.equals` 对 JsonObject 是键序无关的结构化相等。
- js/py 工具自身已强制 data ≤ 32KiB；sanitizer 的 OBJECT 上限须高于该值（64K 字符）以免双写限制冲突。

## Requirements

- R1 `idempotency` 表加 `created_at INTEGER NOT NULL DEFAULT 0`：`DB_VERSION` 1→2，`onCreate` 直接建新 schema，`onUpgrade` 对 `oldVersion < 2` 做 `ALTER TABLE`（不再 DROP 任何表）；v1 遗留行 created_at=0 视为过期。
- R2 `SqliteIdempotencyStore.put` 惰性清理：墙钟每小时至多一次，`DELETE FROM idempotency WHERE created_at < now - 24h`（覆盖遗留 0 行）。
- R3 `SqliteAuditLog.record` 惰性行数上限：每小时至多一次，保留最近 500 行（与 `listRecent` 上限一致），按 id 倒序保留。
- R4 `SqliteTaskStore.deleteByConversation` 在既有事务内、`deleteByTaskId`（新增事务）按 `idempotency_key LIKE taskId%` 级联删除幂等行。
- R5 `ToolCallSanitizer`：STRING 参数超过 10_000 字符 → `INVALID_TOOL_ARGS`；OBJECT 参数序列化超过 64_000 字符或嵌套深度超过 8 层 → `INVALID_TOOL_ARGS`；其余行为不变。
- R6 `isRepeatOfSuccessfulCall` 改结构化比较：两侧 `Json.parseToJsonElement` 后用 `JsonElement.equals`（键序/空白不敏感）；任一侧不可解析时回退到原字符串全等比较。
- R7 不动 14 条冻结契约；不改幂等键格式；不引入 mock/robolectric 依赖。

## Acceptance Criteria

- [x] AC1 JVM 测试（core/runtime）：超长 STRING 拒绝且错误为 `INVALID_TOOL_ARGS`；OBJECT 尺寸超限、深度超限分别拒绝；合法大小（如 js_eval 32KiB 内）通过；既有 sanitizer 测试全绿。
- [x] AC2 JVM 测试（core/llm）：args 键序不同/空白不同仍判 repeat；args 实质不同不误判；不可解析 args 回退字符串比较。
- [x] AC3 `onUpgrade` 无 DROP 语句（代码审查断言），DB_VERSION=2，app 两 flavor 编译通过 + `checkChannelLeak` 绿。
- [x] AC4 全量 `./gradlew :core:model:test :core:tool:test :core:runtime:test :core:llm:test :tool:accessibility:test :app:checkChannelLeak :app:testSideloadDebugUnitTest` 通过。
- [x] AC5 data 层 SQL 行为（TTL/级联/行数上限）列入真机走查清单（删除会话后 `adb shell run-as` 查 idempotency 行数），PRD Out of scope 注明无 JVM 测试的原因。

## Out of scope

- agent_tasks 表加 conversation_id/status 列与索引（查询下推，另立任务）。
- argsSummary 中用户内容（如短信正文）进入 trace/LLM 上下文的处理（属 prompt 预算与注入防护任务）。
- 主线程 DB 恢复迁移（DougieApplication runBlocking，另立任务）。
