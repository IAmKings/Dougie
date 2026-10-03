# 数据与输入卫生 — 执行计划

## Order

1. **Stage 1 — data/tasks**
   - `TaskDbHelper`：DB_VERSION=2、onCreate 新 schema、onUpgrade 增量 ALTER（删 DROP）。
   - `SqliteIdempotencyStore`：put 写 created_at + 惰性 TTL 清理。
   - `SqliteAuditLog`：record 惰性行数上限清理。
   - `SqliteTaskStore`：两个 delete 方法级联清理 idempotency（deleteByTaskId 补事务）。
   - 验证：`./gradlew :app:compileSideloadDebugKotlin :app:compilePlayDebugKotlin`。【回滚点 1】

2. **Stage 2 — core/runtime Sanitizer 上限**
   - `ToolCallSanitizer`：STRING/OBJECT 上限 + jsonDepth；`ToolCallSanitizerTest` 新增 4 类用例。
   - 验证：`./gradlew :core:runtime:test`。【回滚点 2】

3. **Stage 3 — core/llm 结构化重复检测**
   - `LocalToolCallParser`：canonicalJson 比较；`LocalToolCallParserTest` 新增 3 类用例。
   - 验证：`./gradlew :core:llm:test`。【回滚点 3】

4. **Stage 4 — 全量**
   - AC4 全套 gradle 命令；真机走查清单写回任务笔记（data 层 SQL 部分）。

## Validation

- 红→绿验证仅适用于 JVM 可测部分（Sanitizer/Parser：先写测试确认在未修复实现上红）；data 层以编译与真机清单覆盖。

## Rollback points

- 每 Stage 独立 revert；DB 迁移前滚后滚均安全（加列无害）。

## Do not

- 不加测试依赖；不动冻结契约；不 amend；不 push。
